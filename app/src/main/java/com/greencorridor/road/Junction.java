package com.greencorridor.road;

import com.greencorridor.engine.SignalController;
import com.greencorridor.model.Axis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A crossing of the main road with a cross street.
 *
 * <p>The square where the roads overlap (the "box") is a critical section: vehicles of
 * the two axes must never be inside it at the same time. {@link #tryEnterBox} and
 * {@link #exitBox} are synchronized so the check and the update happen atomically.
 */
public class Junction {

    private final int index;
    private final double x;
    private final double y;
    private final List<StopLine> approaches = new ArrayList<StopLine>();
    private final int[] boxOccupancy = new int[Axis.values().length];
    private SignalController signal;

    Junction(int index, double x, double y) {
        this.index = index;
        this.x = x;
        this.y = y;
    }

    void addApproach(StopLine sl) {
        approaches.add(sl);
    }

    /** Lets a vehicle into the box unless traffic of the other axis is still inside. */
    public synchronized boolean tryEnterBox(Axis axis) {
        if (boxOccupancy[axis.other().ordinal()] > 0) {
            return false;
        }
        boxOccupancy[axis.ordinal()]++;
        return true;
    }

    public synchronized void exitBox(Axis axis) {
        if (boxOccupancy[axis.ordinal()] > 0) {
            boxOccupancy[axis.ordinal()]--;
        }
    }

    public synchronized int vehiclesInBox() {
        return boxOccupancy[0] + boxOccupancy[1];
    }

    /** Vehicles approaching on this axis within "range" pixels of the stop lines. */
    public int demand(Axis axis, double range) {
        int n = 0;
        for (StopLine sl : approaches) {
            if (sl.getAxis() == axis) {
                n += sl.getLane().countApproaching(sl, range);
            }
        }
        return n;
    }

    /** Stopped vehicles queued on this axis. */
    public int queued(Axis axis, double range) {
        int n = 0;
        for (StopLine sl : approaches) {
            if (sl.getAxis() == axis) {
                n += sl.getLane().countQueued(sl, range);
            }
        }
        return n;
    }

    public int getIndex() {
        return index;
    }

    /** 1-based number shown to the user. */
    public int getNumber() {
        return index + 1;
    }

    public String getName() {
        return "J" + getNumber();
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public List<StopLine> getApproaches() {
        return Collections.unmodifiableList(approaches);
    }

    public SignalController getSignal() {
        return signal;
    }

    public void setSignal(SignalController signal) {
        this.signal = signal;
    }
}
