package com.greencorridor.engine;

import com.greencorridor.model.Axis;
import com.greencorridor.model.PreemptionEvent;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;
import com.greencorridor.road.Junction;
import com.greencorridor.vehicle.Prioritized;
import com.greencorridor.vehicle.Vehicle;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * The traffic light controller of one junction. It runs on its own thread
 * (implements Runnable) and steps through the {@link Phase} cycle.
 *
 * <p>All shared state is guarded by this object's monitor:
 * <ul>
 *   <li>Vehicles at a red light call {@link #awaitGreen}, which wait()s here.
 *       {@link #setPhase} calls notifyAll() so they wake and check their light again.</li>
 *   <li>Emergency vehicles call {@link #requestPreemption}; the request goes into a
 *       linked list ordered by urgency and the controller thread is woken with notifyAll().</li>
 * </ul>
 * A road that was kept red for an emergency is owed a minimum green before another
 * emergency may take the junction away again, so back-to-back emergencies cannot starve it.
 */
public class SignalController implements Runnable, Monitorable {

    private final Junction junction;
    private final SimulationEngine engine;
    private final SignalMode mode;
    private final long mainGreenMs;
    private final long crossGreenMs;
    private final long yellowMs;

    private volatile Phase phase = Phase.EW_GREEN;
    private volatile long phaseStart;
    private volatile Axis priorityAxis;              // axis held green for an emergency, or null
    private volatile int ewDemand;
    private volatile int nsDemand;
    private boolean manualSwitch;
    private final LinkedList<Request> requests = new LinkedList<Request>();
    /** Axes kept red to let an emergency vehicle through; they are owed a full minimum green. */
    private final boolean[] starved = new boolean[Axis.values().length];
    private long protectedUntil;                     // the current green may not be cut before this

    public SignalController(Junction junction, SimulationEngine engine, Scenario scenario, SignalMode mode) {
        this.junction = junction;
        this.engine = engine;
        this.mode = mode;
        this.mainGreenMs = scenario.getMainGreenSec() * 1000L;
        this.crossGreenMs = scenario.getCrossGreenSec() * 1000L;
        this.yellowMs = scenario.getYellowSec() * 1000L;
    }

    @Override
    public void run() {
        SimClock clock = engine.getClock();
        synchronized (this) {
            phaseStart = clock.now();
        }
        try {
            while (engine.isRunning()) {
                clock.awaitIfPaused();
                // Count vehicles before taking our own lock: the lanes have locks of their own.
                int ew = junction.demand(Axis.EW, SimConfig.DEMAND_RANGE);
                int ns = junction.demand(Axis.NS, SimConfig.DEMAND_RANGE);
                tick(clock.now(), ew, ns);
                synchronized (this) {
                    wait(SimConfig.TICK_MS);          // a pre-emption request wakes us early
                }
            }
        } catch (InterruptedException e) {
            // run stopped
        }
    }

    /** One decision step. Package-private so the unit tests can drive it directly. */
    synchronized void tick(long now, int ew, int ns) {
        ewDemand = ew;
        nsDemand = ns;
        Request head = requests.peek();
        if (head != null) {
            preemptionTick(now, head);
        } else {
            priorityAxis = null;
            normalTick(now, ew, ns);
        }
    }

    private void normalTick(long now, int ew, int ns) {
        long elapsed = now - phaseStart;
        switch (phase.getLight()) {
            case GREEN:
                Axis g = phase.getAxis();
                int here = g == Axis.EW ? ew : ns;
                int there = g == Axis.EW ? ns : ew;
                if (manualSwitch || greenIsOver(g, elapsed, here, there)) {
                    manualSwitch = false;
                    setPhase(phase.next(), now);
                }
                break;
            case YELLOW:
                if (elapsed >= yellowMs) {
                    setPhase(phase.next(), now);
                }
                break;
            default:                                   // all-red clearance
                if (elapsed >= SimConfig.ALL_RED_MS) {
                    setPhase(phase.next(), now);
                }
                break;
        }
    }

    private boolean greenIsOver(Axis axis, long elapsed, int here, int there) {
        long green = greenTimeOf(axis);
        if (mode == SignalMode.FIXED) {
            return elapsed >= green;
        }
        // ADAPTIVE (vehicle-actuated control)
        if (elapsed < SimConfig.MIN_GREEN_MS) {
            return false;                              // every green lasts a minimum time
        }
        if (there == 0) {
            return false;                              // nobody waiting on the other road: stay green
        }
        if (here == 0) {
            return true;                               // our road is empty: "gap out" and switch
        }
        return elapsed >= 2 * green;                   // "max out" at twice the base green
    }

    /** Drives the lights towards green for the most urgent emergency vehicle. */
    private void preemptionTick(long now, Request head) {
        Axis target = head.axis;
        priorityAxis = target;
        starved[target.other().ordinal()] = true;
        long elapsed = now - phaseStart;
        if (phase.isGreenFor(target)) {
            for (Request r : requests) {
                if (r.axis == target && r.greenAt < 0) {
                    r.greenAt = now;
                    engine.log(String.format("%s: green for %s after %.1f s", junction.getName(),
                            r.vehicle.getLabel(), (now - r.requestedAt) / 1000.0));
                }
            }
            return;                                    // hold the green while the vehicle comes
        }
        switch (phase.getLight()) {
            case GREEN:
                // The other road is green: end it now, unless that road had been starved by an
                // earlier emergency and has not yet had its minimum green (fairness).
                if (now >= protectedUntil) {
                    setPhase(Phase.yellowFor(phase.getAxis()), now);
                }
                break;
            case YELLOW:
                if (elapsed >= yellowMs) {
                    setPhase(phase.next(), now);
                }
                break;
            default:
                if (elapsed >= SimConfig.ALL_RED_MS) {
                    setPhase(Phase.greenFor(target), now);
                }
                break;
        }
    }

    private void setPhase(Phase next, long now) {
        phase = next;
        phaseStart = now;
        if (next.getLight() == SignalLight.GREEN && starved[next.getAxis().ordinal()]) {
            starved[next.getAxis().ordinal()] = false;
            protectedUntil = now + SimConfig.MIN_GREEN_MS;
        }
        notifyAll();                                   // wake vehicles waiting for their green
    }

    private long greenTimeOf(Axis axis) {
        return axis == Axis.EW ? mainGreenMs : crossGreenMs;
    }

    // ----- called by vehicle threads -----

    public boolean isGreen(Axis axis) {
        return phase.isGreenFor(axis);
    }

    /**
     * May a vehicle "dist" pixels before the stop line, going at "speed", enter now?
     * On yellow only vehicles too close to stop safely keep going.
     */
    public boolean mayEnter(Axis axis, double dist, double speed) {
        switch (phase.lightFor(axis)) {
            case GREEN:
                return true;
            case YELLOW:
                return speed > SimConfig.STOPPED_SPEED && dist <= speed * SimConfig.YELLOW_COMMIT_SEC;
            default:
                return false;
        }
    }

    /**
     * Blocks the calling vehicle thread until the given axis has a green light, or until
     * the vehicle must pull over for an emergency vehicle behind it.
     */
    public synchronized void awaitGreen(Axis axis, Vehicle waiter) throws InterruptedException {
        while (!phase.isGreenFor(axis) && !waiter.mustGiveWay()) {
            wait();
        }
    }

    /** An emergency vehicle wakes the vehicles waiting here so they can pull over. */
    public synchronized void wakeWaitingVehicles() {
        notifyAll();
    }

    /** An emergency vehicle asks for a green light on its axis. */
    public void requestPreemption(Vehicle vehicle, Axis axis, long now) {
        synchronized (this) {
            if (find(vehicle) != null) {
                return;
            }
            Request r = new Request(vehicle, axis, now);
            // Keep the list ordered: higher urgency first, equal urgency first-come-first-served.
            ListIterator<Request> it = requests.listIterator();
            while (it.hasNext()) {
                if (it.next().urgency < r.urgency) {
                    it.previous();
                    break;
                }
            }
            it.add(r);
            notifyAll();                               // wake the controller thread at once
        }
        engine.log(String.format("%s: %s (%s) requests a green light", junction.getName(),
                vehicle.getLabel(), vehicle.getLane().getDirection().getLabel()));
    }

    /** The emergency vehicle has left the junction: hand the junction back to normal control. */
    public void releasePreemption(Vehicle vehicle, long now) {
        PreemptionEvent event;
        synchronized (this) {
            Request r = find(vehicle);
            if (r == null) {
                return;
            }
            requests.remove(r);
            if (r.greenAt < 0 && phase.isGreenFor(r.axis)) {
                r.greenAt = now;
            }
            event = new PreemptionEvent(junction.getNumber(), vehicle.getNumber(), vehicle.getTypeCode(),
                    vehicle.getLane().getDirection(), r.requestedAt, r.greenAt, now);
            if (requests.isEmpty()) {
                priorityAxis = null;
                if (phase.getLight() == SignalLight.GREEN) {
                    // Recovery: the held green ends a few seconds from now.
                    long limit = mode == SignalMode.FIXED ? greenTimeOf(phase.getAxis()) : SimConfig.MIN_GREEN_MS;
                    phaseStart = now - Math.max(0, limit - SimConfig.RECOVERY_GREEN_MS);
                }
            }
            notifyAll();
        }
        // Outside the lock: handing the event to the logger may block if its buffer is full.
        engine.preemptionFinished(event);
    }

    /** Traffic-police override from a mouse click: end the current green early. */
    public synchronized boolean forceSwitch() {
        if (!requests.isEmpty() || phase.getLight() != SignalLight.GREEN) {
            return false;
        }
        manualSwitch = true;
        notifyAll();
        return true;
    }

    private Request find(Vehicle vehicle) {
        for (Request r : requests) {
            if (r.vehicle == vehicle) {
                return r;
            }
        }
        return null;
    }

    // ----- read by the user interface -----

    public Phase getPhase() {
        return phase;
    }

    public SignalLight getLight(Axis axis) {
        return phase.lightFor(axis);
    }

    public long getPhaseElapsedMs(long now) {
        return Math.max(0, now - phaseStart);
    }

    public Axis getPriorityAxis() {
        return priorityAxis;
    }

    /** Label of the vehicle currently being given priority, or null. */
    public synchronized String getPriorityHolder() {
        Request head = requests.peek();
        return head == null ? null : head.vehicle.getLabel();
    }

    public int getDemand(Axis axis) {
        return axis == Axis.EW ? ewDemand : nsDemand;
    }

    public Junction getJunction() {
        return junction;
    }

    public SignalMode getMode() {
        return mode;
    }

    @Override
    public String getRole() {
        return "Signal controller";
    }

    @Override
    public String getStatusText() {
        String holder = getPriorityHolder();
        String text = String.format("%s %s for %.1f s | approaching: main %d, cross %d", junction.getName(), phase.describe(),
                getPhaseElapsedMs(engine.getClock().now()) / 1000.0, ewDemand, nsDemand);
        return holder == null ? text : text + " | PRIORITY for " + holder;
    }

    /** A pending request for priority. */
    private static class Request {
        final Vehicle vehicle;
        final Axis axis;
        final long requestedAt;
        final int urgency;
        long greenAt = -1;

        Request(Vehicle vehicle, Axis axis, long requestedAt) {
            this.vehicle = vehicle;
            this.axis = axis;
            this.requestedAt = requestedAt;
            this.urgency = vehicle instanceof Prioritized ? ((Prioritized) vehicle).getUrgency() : 0;
        }
    }
}
