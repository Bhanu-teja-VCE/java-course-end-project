package com.greencorridor.vehicle;

import com.greencorridor.engine.Monitorable;
import com.greencorridor.engine.SignalController;
import com.greencorridor.engine.SimClock;
import com.greencorridor.engine.SimConfig;
import com.greencorridor.engine.SimulationEngine;
import com.greencorridor.model.TripRecord;
import com.greencorridor.road.Lane;
import com.greencorridor.road.StopLine;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Base class of every vehicle. Each vehicle runs on its own thread: {@link #run()} moves
 * it a little, sleeps for one tick and repeats until it leaves the map.
 *
 * <p>While it drives, the vehicle:
 * <ul>
 *   <li>keeps a safe gap behind the vehicle ahead (read from the shared lane),</li>
 *   <li>stops at the stop line when its light is not green. Once stopped it calls
 *       {@link SignalController#awaitGreen}, which puts the thread in the WAITING
 *       state until the controller calls notifyAll(),</li>
 *   <li>pulls over to the left edge when an emergency vehicle comes up behind it,</li>
 *   <li>enters the junction box only through the synchronized
 *       {@link com.greencorridor.road.Junction#tryEnterBox}.</li>
 * </ul>
 * Subclasses decide how the vehicle looks and how fast it is. Emergency vehicles also
 * override the hooks {@link #beforeStep} and {@link #onLeaveJunction} to ask junctions
 * for a green light.
 */
public abstract class Vehicle implements Runnable, Drawable, Monitorable {

    private static final Color BRAKE_LIGHT = new Color(255, 40, 40);
    private static int counter;

    protected final int number;
    protected final double length;
    protected final double width;
    protected final double maxSpeed;       // px per simulated second
    protected final double acceleration;   // px per second squared

    protected SimulationEngine engine;
    protected Lane lane;
    private Thread thread;

    // Read by other threads (vehicles behind, signal controllers, the UI), so volatile.
    private volatile double frontS;
    private volatile double speed;
    private volatile boolean waitingAtSignal;
    private volatile SignalController waitingOn;
    private volatile boolean yielding;     // pulled over for an emergency vehicle
    private volatile double lateral;       // sideways shift to the driver's left, in px
    private volatile boolean exited;
    private volatile long waitMs;
    private volatile int stops;

    // Used only by this vehicle's own thread (or set before it starts).
    private long arrivalSimMs = -1;        // reached the edge of the map (may then queue)
    private double startS;                 // where on the lane it started
    private long lastUpdateMs;
    private long entrySimMs = -1;
    private long startAt = -1;
    private boolean stopped;
    private StopLine boxStop;              // junction box the vehicle is inside, or null

    protected Vehicle(double length, double width, double maxSpeed, double acceleration) {
        this.number = nextNumber();
        this.length = length;
        this.width = width;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
    }

    private static synchronized int nextNumber() {
        return ++counter;
    }

    /** Vehicle numbers start again from 1 in every run. */
    public static synchronized void resetNumbering() {
        counter = 0;
    }

    // ----- what subclasses provide -----

    /** Code stored in the database, for example "CAR". */
    public abstract String getTypeCode();

    /** Name shown to the user, for example "Car". */
    public abstract String getDisplayName();

    /** Draws the vehicle pointing along +x, centred on (0, 0). */
    protected abstract void drawBody(Graphics2D g, long animMillis);

    public int getThreadPriority() {
        return Thread.NORM_PRIORITY;
    }

    public boolean isEmergency() {
        return false;
    }

    /** Hook called at the start of every step. */
    protected void beforeStep(long now) {
    }

    /** Hook called when the vehicle has completely left a junction box. */
    protected void onLeaveJunction(StopLine stopLine, long now) {
    }

    /** Hook called once when the vehicle's thread ends, for whatever reason. */
    protected void onRemoved(long now) {
    }

    // ----- life cycle -----

    /** Connects the vehicle to its engine, lane and thread before the thread starts. */
    public void attach(SimulationEngine engine, Lane lane, Thread thread) {
        this.engine = engine;
        this.lane = lane;
        this.thread = thread;
    }

    /** Time the vehicle reached the edge of the map. Its delay is counted from here. */
    public void markArrival(long simMs) {
        arrivalSimMs = simMs;
    }

    /** Called by the lane when the vehicle drives onto it, with its front at position s. */
    public void placeAtEntry(double s, double leaderSpeed) {
        frontS = s;
        startS = s;
        double entrySpeed = maxSpeed * 0.8;
        speed = leaderSpeed < 0 ? entrySpeed : Math.min(entrySpeed, leaderSpeed);
        lateral = isEmergency() ? -SimConfig.EMERGENCY_SHIFT : 0;
    }

    /** The body of the vehicle's thread. It is final so subclasses cannot break the loop. */
    @Override
    public final void run() {
        SimClock clock = engine.getClock();
        lastUpdateMs = clock.now();
        entrySimMs = lastUpdateMs;
        long exitMs = -1;
        try {
            while (engine.isRunning()) {
                clock.awaitIfPaused();
                long now = clock.now();
                if (step(now)) {
                    exitMs = now;
                    break;
                }
                Thread.sleep(SimConfig.TICK_MS);       // TIMED_WAITING until the next tick
            }
        } catch (InterruptedException e) {
            // The engine interrupts every vehicle when the run stops: just end the thread.
        } finally {
            long now = clock.now();
            leaveBox(now);
            lane.leave(this);
            exited = true;
            onRemoved(now);
            engine.getRegistry().markTerminated(Thread.currentThread());
        }
        if (exitMs >= 0) {
            engine.vehicleFinished(this, buildTrip(exitMs));
        }
    }

    /**
     * Moves the vehicle by one time step.
     *
     * @return true when the vehicle has left the map
     */
    private boolean step(long now) throws InterruptedException {
        long elapsed = now - lastUpdateMs;
        lastUpdateMs = now;
        if (speed < SimConfig.STOPPED_SPEED) {
            waitMs += elapsed;
        }
        double dt = Math.min(elapsed, SimConfig.MAX_STEP_MS) / 1000.0;
        if (dt <= 0) {
            return false;
        }

        beforeStep(now);
        if (!isEmergency()) {
            yielding = mustGiveWay();
        }
        updateLateral(dt);

        // 1. How far may we go? First limit: the vehicle ahead.
        Vehicle leader = lane.leaderOf(this);
        double allowed = leader == null ? Double.MAX_VALUE
                : leader.getRearS() - frontS - SimConfig.MIN_GAP;

        // Second limit: the next stop line, if its light (or a full exit) says stop.
        StopLine next = lane.nextStopLine(frontS);
        if (next != null && next == boxStop) {
            next = null;                               // already inside that junction: committed
        }
        boolean held = false;
        if (next != null) {
            double dist = next.getStopS() - frontS;
            if (dist < allowed && !mayCross(next, dist, leader)) {
                allowed = dist;
                held = true;
            }
        }

        // 2. Speed and distance for this step.
        double v = targetSpeed(allowed, dt, now);
        double move = Math.min(v * dt, Math.max(0, allowed));

        // 3. Crossing the stop line means entering the junction box: a critical section.
        if (next != null && !held && frontS + move > next.getStopS()) {
            if (next.getJunction().tryEnterBox(next.getAxis())) {
                boxStop = next;
            } else {
                move = Math.max(0, next.getStopS() - frontS);
                held = true;
            }
        }

        frontS += move;
        speed = move / dt;
        countStops();

        if (boxStop != null && getRearS() > boxStop.getBoxExitS()) {
            leaveBox(now);
        }

        // 4. Standing at a red light: sleep on the signal's monitor until it turns green.
        if (held && speed < SimConfig.STOPPED_SPEED && next.getStopS() - frontS < 1.0) {
            SignalController signal = next.getJunction().getSignal();
            if (!signal.isGreen(next.getAxis()) && !yielding) {
                waitingAtSignal = true;
                waitingOn = signal;
                try {
                    signal.awaitGreen(next.getAxis(), this);
                } finally {
                    waitingAtSignal = false;
                    waitingOn = null;
                }
                startAt = engine.getClock().now() + SimConfig.REACTION_MS;
            }
        }

        return getRearS() > lane.getLength();
    }

    /** May the vehicle drive past this stop line now? */
    private boolean mayCross(StopLine next, double dist, Vehicle leader) {
        if (!next.getJunction().getSignal().mayEnter(next.getAxis(), dist, speed)) {
            return false;
        }
        // "Don't block the box": enter only if there is room for us behind the queue on
        // the far side, otherwise we would get stuck in the middle of the crossing and
        // block the cross traffic's green.
        if (lane.roomBeyond(next, this) < length + SimConfig.MIN_GAP) {
            return false;
        }
        return leader == null
                || leader.getSpeed() >= SimConfig.CRAWL_SPEED
                || leader.getRearS() - next.getBoxExitS() >= length + SimConfig.MIN_GAP;
    }

    /**
     * True if this (ordinary) vehicle should pull over: an emergency vehicle is right
     * behind it. Never inside a junction box, where stopping would block the crossing.
     */
    public boolean mustGiveWay() {
        return !isEmergency() && boxStop == null && lane.mustGiveWay(this, SimConfig.YIELD_RANGE);
    }

    private void updateLateral(double dt) {
        double target = isEmergency() ? -SimConfig.EMERGENCY_SHIFT : (yielding ? SimConfig.YIELD_SHIFT : 0);
        double maxMove = SimConfig.LATERAL_SPEED * dt;
        double diff = target - lateral;
        lateral += Math.max(-maxMove, Math.min(maxMove, diff));
    }

    private double targetSpeed(double allowed, double dt, long now) {
        if (allowed <= 0.5) {
            return 0;
        }
        if (yielding) {
            // pull over: brake firmly to a stop at the side of the road
            return Math.min(Math.max(0, speed - 2 * SimConfig.BRAKE_DECEL * dt),
                    Math.sqrt(2 * SimConfig.BRAKE_DECEL * allowed));
        }
        if (speed < SimConfig.STOPPED_SPEED) {
            // A standing driver needs a moment to react before moving off.
            if (startAt < 0) {
                startAt = now + SimConfig.REACTION_MS;
            }
            if (now < startAt) {
                return 0;
            }
        } else {
            startAt = -1;
        }
        double v = Math.min(maxSpeed, speed + acceleration * dt);
        // Brake smoothly so we can stop within the allowed distance.
        return Math.min(v, Math.sqrt(2 * SimConfig.BRAKE_DECEL * allowed));
    }

    private void countStops() {
        if (speed < SimConfig.STOPPED_SPEED) {
            if (!stopped) {
                stopped = true;
                stops++;
            }
        } else if (speed > SimConfig.STOPPED_SPEED * 3) {
            stopped = false;
        }
    }

    private void leaveBox(long now) {
        if (boxStop != null) {
            StopLine sl = boxStop;
            boxStop = null;
            sl.getJunction().exitBox(sl.getAxis());
            onLeaveJunction(sl, now);
        }
    }

    private TripRecord buildTrip(long exitMs) {
        long startMs = arrivalSimMs >= 0 ? arrivalSimMs : entrySimMs;
        double idealMs = (lane.getLength() + length - startS) / maxSpeed * 1000.0;
        double delaySec = Math.max(0, (exitMs - startMs - idealMs) / 1000.0);
        long queuedMs = entrySimMs - startMs;          // time spent waiting off the map
        return new TripRecord(number, getTypeCode(), isEmergency(), lane.getDirection(),
                startMs, exitMs, delaySec, (waitMs + queuedMs) / 1000.0, stops);
    }

    // ----- drawing -----

    @Override
    public final void draw(Graphics2D g, long animMillis) {
        AffineTransform saved = g.getTransform();
        g.translate(getCenterX(), getCenterY());
        g.rotate(lane.getDirection().angle());
        drawBody(g, animMillis);
        if (speed < SimConfig.STOPPED_SPEED) {
            g.setColor(BRAKE_LIGHT);
            double rx = -length / 2 - 1;
            if (width < 8) {
                g.fill(new Rectangle2D.Double(rx, -1.2, 2.4, 2.4));
            } else {
                g.fill(new Rectangle2D.Double(rx, -width / 2 + 1, 2.4, 2.6));
                g.fill(new Rectangle2D.Double(rx, width / 2 - 3.6, 2.4, 2.6));
            }
        }
        g.setTransform(saved);
    }

    /** Fills the outline of the vehicle in one colour with a thin darker border. */
    protected void fillBody(Graphics2D g, Color color, double arc) {
        RoundRectangle2D body = new RoundRectangle2D.Double(-length / 2, -width / 2, length, width, arc, arc);
        g.setColor(color);
        g.fill(body);
        g.setColor(color.darker());
        g.draw(body);
    }

    /** True if the world point (px, py) lies on this vehicle (used for mouse hover). */
    public boolean contains(double px, double py) {
        double dx = px - getCenterX();
        double dy = py - getCenterY();
        double a = -lane.getDirection().angle();
        double lx = dx * Math.cos(a) - dy * Math.sin(a);
        double ly = dx * Math.sin(a) + dy * Math.cos(a);
        return Math.abs(lx) <= length / 2 + 3 && Math.abs(ly) <= width / 2 + 3;
    }

    // ----- Monitorable -----

    @Override
    public String getRole() {
        return getDisplayName();
    }

    @Override
    public String getStatusText() {
        if (exited) {
            return "Left " + lane.describe();
        }
        if (thread != null && thread.getState() == Thread.State.NEW) {
            if (engine.isApproaching(this)) {
                return "Approaching the " + lane.describe() + " (thread not started)";
            }
            return "Arrived - queued to enter " + lane.describe() + " (thread not started)";
        }
        if (waitingAtSignal) {
            return lane.describe() + ": red light, called wait() on the signal";
        }
        if (yielding) {
            return lane.describe() + ": pulled over for an emergency vehicle";
        }
        return String.format("%s: %.0f km/h, %.0f m along", lane.describe(), getSpeedKmh(),
                frontS / SimConfig.PX_PER_METRE);
    }

    // ----- getters -----

    public int getNumber() {
        return number;
    }

    /** For example "Ambulance #7". */
    public String getLabel() {
        return getDisplayName() + " #" + number;
    }

    public double getLength() {
        return length;
    }

    public double getWidth() {
        return width;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getFrontS() {
        return frontS;
    }

    public double getRearS() {
        return frontS - length;
    }

    public double getSpeed() {
        return speed;
    }

    public double getSpeedKmh() {
        return speed / SimConfig.PX_PER_METRE * 3.6;
    }

    public double getCenterX() {
        return lane.xAt(frontS - length / 2) + lane.getDirection().getLeftX() * lateral;
    }

    public double getCenterY() {
        return lane.yAt(frontS - length / 2) + lane.getDirection().getLeftY() * lateral;
    }

    public Lane getLane() {
        return lane;
    }

    public Thread getThread() {
        return thread;
    }

    public boolean isWaitingAtSignal() {
        return waitingAtSignal;
    }

    /** The signal this vehicle's thread is waiting on, or null. */
    public SignalController getWaitingOn() {
        return waitingOn;
    }

    public boolean isYielding() {
        return yielding;
    }

    /** Name of the junction whose box the vehicle is in, or null (for diagnostics). */
    public String getBoxJunctionName() {
        StopLine b = boxStop;
        return b == null ? null : b.getJunction().getName();
    }

    public double getWaitSec() {
        return waitMs / 1000.0;
    }

    public int getStops() {
        return stops;
    }
}
