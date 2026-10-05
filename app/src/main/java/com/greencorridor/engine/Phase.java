package com.greencorridor.engine;

import com.greencorridor.model.Axis;

/**
 * The six phases of a junction's signal cycle, in order. A CLEARANCE phase is the
 * all-red pause that lets the last vehicles get out of the box before the other road
 * gets green.
 */
public enum Phase {
    EW_GREEN(Axis.EW, SignalLight.GREEN),
    EW_YELLOW(Axis.EW, SignalLight.YELLOW),
    EW_CLEARANCE(Axis.EW, SignalLight.RED),
    NS_GREEN(Axis.NS, SignalLight.GREEN),
    NS_YELLOW(Axis.NS, SignalLight.YELLOW),
    NS_CLEARANCE(Axis.NS, SignalLight.RED);

    private final Axis axis;
    private final SignalLight light;

    Phase(Axis axis, SignalLight light) {
        this.axis = axis;
        this.light = light;
    }

    /** The axis this phase belongs to. */
    public Axis getAxis() {
        return axis;
    }

    /** The light shown to this phase's own axis. */
    public SignalLight getLight() {
        return light;
    }

    /** The light a vehicle on the given axis sees during this phase. */
    public SignalLight lightFor(Axis a) {
        return a == axis ? light : SignalLight.RED;
    }

    public boolean isGreenFor(Axis a) {
        return a == axis && light == SignalLight.GREEN;
    }

    /** For example "Main road green" or "All red". */
    public String describe() {
        if (light == SignalLight.RED) {
            return "All red";
        }
        return (axis == Axis.EW ? "Main road " : "Cross street ") + light.name().toLowerCase();
    }

    public Phase next() {
        Phase[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Phase greenFor(Axis a) {
        return a == Axis.EW ? EW_GREEN : NS_GREEN;
    }

    public static Phase yellowFor(Axis a) {
        return a == Axis.EW ? EW_YELLOW : NS_YELLOW;
    }
}
