package com.greencorridor.road;

import com.greencorridor.engine.SimConfig;
import com.greencorridor.model.Direction;
import com.greencorridor.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * One straight lane with a single direction of travel. Positions along the lane are
 * measured by "s", the distance in pixels from the lane's start point.
 *
 * <p>Two linked lists are shared between threads, so every method that touches them is
 * synchronized on the lane:
 * <ul>
 *   <li>{@code vehicles}: vehicles on the road, front-most first, so the vehicle ahead
 *       of you is the previous element. Only emergency vehicles overtake, and only
 *       vehicles that have pulled over for them.</li>
 *   <li>{@code entryQueue}: vehicles that have arrived but cannot enter yet because the
 *       start of the lane is occupied. Their threads are created but not started (NEW).</li>
 * </ul>
 * These methods never call into other locked objects, which rules out deadlock.
 */
public class Lane {

    private final int id;
    private final Direction direction;
    private final double startX;
    private final double startY;
    private final double length;
    private final Junction crossJunction;   // for cross-street lanes; null on the main road
    private final List<StopLine> stopLines = new ArrayList<StopLine>();

    private final LinkedList<Vehicle> vehicles = new LinkedList<Vehicle>();
    private final LinkedList<Vehicle> entryQueue = new LinkedList<Vehicle>();

    Lane(int id, Direction direction, double startX, double startY, double length, Junction crossJunction) {
        this.id = id;
        this.direction = direction;
        this.startX = startX;
        this.startY = startY;
        this.length = length;
        this.crossJunction = crossJunction;
    }

    /** Adds the stop line of a junction this lane passes through (done while building the map). */
    StopLine addStopLine(Junction junction) {
        double centreS = positionOf(junction.getX(), junction.getY());
        double boxEntry = centreS - SimConfig.HALF_ROAD;
        StopLine sl = new StopLine(this, junction, boxEntry - SimConfig.STOP_MARGIN, boxEntry,
                centreS + SimConfig.HALF_ROAD);
        stopLines.add(sl);
        Collections.sort(stopLines, (a, b) -> Double.compare(a.getStopS(), b.getStopS()));
        return sl;
    }

    // ----- geometry (no shared state, no locking needed) -----

    public double xAt(double s) {
        return startX + direction.getDx() * s;
    }

    public double yAt(double s) {
        return startY + direction.getDy() * s;
    }

    /** Distance along the lane of the point closest to (x, y). */
    public double positionOf(double x, double y) {
        return (x - startX) * direction.getDx() + (y - startY) * direction.getDy();
    }

    /** First stop line in front of a vehicle whose front is at frontS, or null. */
    public StopLine nextStopLine(double frontS) {
        for (StopLine sl : stopLines) {
            if (sl.getStopS() >= frontS - 0.01) {
                return sl;
            }
        }
        return null;
    }

    // ----- vehicles on the lane -----

    /**
     * The vehicle v has to follow, or null if the road ahead is clear.
     * <ul>
     *   <li>An emergency vehicle squeezes past vehicles that have pulled over, so it
     *       follows the first vehicle ahead that has not.</li>
     *   <li>While an emergency vehicle is passing, it and a pulled-over vehicle are side by
     *       side, so list order alone is not enough: an ordinary vehicle follows whichever
     *       of them has its rear bumper nearest.</li>
     * </ul>
     */
    public synchronized Vehicle leaderOf(Vehicle v) {
        int index = vehicles.indexOf(v);
        Vehicle leader = null;
        for (int i = index - 1; i >= 0; i--) {
            Vehicle ahead = vehicles.get(i);
            if (v.isEmergency()) {
                if (ahead.isYielding()) {
                    continue;
                }
                return ahead;
            }
            if (leader == null || ahead.getRearS() < leader.getRearS()) {
                leader = ahead;
            }
            if (!ahead.isEmergency() && !ahead.isYielding()) {
                break;                      // an ordinary vehicle in the lane: nothing beyond matters
            }
        }
        return leader;
    }

    /**
     * Should v pull over? Yes if an emergency vehicle is close behind it and every
     * vehicle in between has pulled over too, or if one has just passed it and is still
     * alongside.
     *
     * <p>The "every vehicle in between" rule prevents a circular wait: a vehicle that
     * cannot pull over (for example one inside a junction box) must still be able to
     * drive on, so the vehicles in front of it must not stop for the siren.
     */
    public synchronized boolean mustGiveWay(Vehicle v, double range) {
        int index = vehicles.indexOf(v);
        if (index < 0) {
            return false;
        }
        for (int k = index + 1; k < vehicles.size(); k++) {
            Vehicle behind = vehicles.get(k);
            if (behind.isEmergency()) {
                return behind.getFrontS() >= v.getRearS() - range;
            }
            if (!behind.isYielding()) {
                break;                      // someone between us and the siren is not moving aside
            }
        }
        for (int k = index - 1; k >= 0; k--) {
            Vehicle ahead = vehicles.get(k);
            if (ahead.getRearS() > v.getFrontS() + SimConfig.MIN_GAP) {
                break;
            }
            if (ahead.isEmergency()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Swaps an emergency vehicle in front of the pulled-over vehicles it has driven past,
     * so the list stays in road order.
     */
    public synchronized void overtakeYielding(Vehicle emergency) {
        int i = vehicles.indexOf(emergency);
        while (i > 0) {
            Vehicle ahead = vehicles.get(i - 1);
            if (!ahead.isYielding() || emergency.getFrontS() <= ahead.getFrontS()) {
                break;
            }
            vehicles.set(i - 1, emergency);
            vehicles.set(i, ahead);
            i--;
        }
    }

    /** Vehicles ahead of v whose rear is within "range" pixels of v's front. */
    public synchronized List<Vehicle> vehiclesAhead(Vehicle v, double range) {
        List<Vehicle> result = new ArrayList<Vehicle>();
        int index = vehicles.indexOf(v);
        for (int i = index - 1; i >= 0; i--) {
            Vehicle ahead = vehicles.get(i);
            if (ahead.getRearS() - v.getFrontS() > range) {
                break;
            }
            result.add(ahead);
        }
        return result;
    }

    public synchronized void leave(Vehicle v) {
        vehicles.remove(v);
    }

    public synchronized List<Vehicle> snapshot() {
        return new ArrayList<Vehicle>(vehicles);
    }

    public synchronized int vehicleCount() {
        return vehicles.size();
    }

    /** Vehicles between "range" pixels before the stop line and the stop line itself. */
    public synchronized int countApproaching(StopLine sl, double range) {
        int n = 0;
        for (Vehicle v : vehicles) {
            double front = v.getFrontS();
            if (front <= sl.getStopS() + 0.5 && front >= sl.getStopS() - range) {
                n++;
            }
        }
        return n;
    }

    /** Stopped vehicles queued in front of the stop line. */
    public synchronized int countQueued(StopLine sl, double range) {
        int n = 0;
        for (Vehicle v : vehicles) {
            double front = v.getFrontS();
            if (v.getSpeed() < SimConfig.STOPPED_SPEED
                    && front <= sl.getStopS() + 0.5 && front >= sl.getStopS() - range) {
                n++;
            }
        }
        return n;
    }

    /**
     * Room left at the tail of the queue between this junction and the next stop line.
     * Returns {@link Double#MAX_VALUE} while traffic beyond the junction keeps flowing:
     * no vehicle there is crawling and the next light is green. Used for the
     * "don't block the box" rule. An emergency vehicle ignores vehicles that have
     * pulled over, because it drives past them.
     */
    public synchronized double roomBeyond(StopLine sl, Vehicle asker) {
        int index = stopLines.indexOf(sl);
        StopLine following = index + 1 < stopLines.size() ? stopLines.get(index + 1) : null;
        double end = following != null ? following.getStopS() : length;
        double used = 0;
        // A red light ahead means a queue is about to form even if everyone still moves.
        boolean queueFormed = following != null
                && !following.getJunction().getSignal().isGreen(following.getAxis());
        for (Vehicle v : vehicles) {
            if (asker.isEmergency() && v.isYielding()) {
                continue;
            }
            double front = v.getFrontS();
            if (front > sl.getStopS() && front <= end) {
                used += v.getLength() + SimConfig.MIN_GAP;
                if (v.getSpeed() < SimConfig.CRAWL_SPEED) {
                    queueFormed = true;
                }
            }
        }
        return queueFormed ? (end - sl.getBoxExitS()) - used : Double.MAX_VALUE;
    }

    // ----- entry queue -----

    /**
     * Puts a newly arrived vehicle in the entry queue. Emergency vehicles jump to the
     * front of the queue; ordinary vehicles are turned away when the queue is full.
     *
     * @return false if the vehicle was turned away
     */
    public synchronized boolean enqueue(Vehicle v, boolean priority) {
        if (priority) {
            entryQueue.addFirst(v);
            return true;
        }
        if (entryQueue.size() >= SimConfig.ENTRY_QUEUE_CAP) {
            return false;
        }
        entryQueue.addLast(v);
        return true;
    }

    /**
     * Moves the first queued vehicle onto the lane if there is room at the start.
     *
     * @return the vehicle that entered, or null
     */
    public synchronized Vehicle admitNext() {
        if (entryQueue.isEmpty()) {
            return null;
        }
        Vehicle next = entryQueue.getFirst();
        double startS = 0;
        double leaderSpeed = -1;
        if (!vehicles.isEmpty()) {
            Vehicle last = vehicles.getLast();
            if (last.getRearS() < SimConfig.MIN_GAP + 1) {
                if (!next.isEmergency() || last.getSpeed() >= SimConfig.STOPPED_SPEED) {
                    return null;        // the start of the lane is still occupied
                }
                // A queue reaches back to the map edge. An emergency vehicle joins behind
                // it anyway (still off the map); the queue then pulls over to let it pass.
                startS = Math.min(0, last.getRearS() - SimConfig.MIN_GAP);
            }
            leaderSpeed = last.getSpeed();
        }
        entryQueue.removeFirst();
        next.placeAtEntry(startS, leaderSpeed);
        vehicles.addLast(next);
        return next;
    }

    public synchronized int entryQueueSize() {
        return entryQueue.size();
    }

    // ----- getters -----

    public int getId() {
        return id;
    }

    public Direction getDirection() {
        return direction;
    }

    public double getLength() {
        return length;
    }

    public boolean isMainRoad() {
        return crossJunction == null;
    }

    public Junction getCrossJunction() {
        return crossJunction;
    }

    public List<StopLine> getStopLines() {
        return Collections.unmodifiableList(stopLines);
    }

    /** Point where the lane enters the visible map (used for the clickable entry arrows). */
    public double getEntryX() {
        return xAt(SimConfig.ENTRY_MARGIN);
    }

    public double getEntryY() {
        return yAt(SimConfig.ENTRY_MARGIN);
    }

    /** For example "Eastbound main road" or "Southbound at J2". */
    public String describe() {
        return direction.getLabel() + (isMainRoad() ? " main road" : " at " + crossJunction.getName());
    }

    @Override
    public String toString() {
        return describe();
    }
}
