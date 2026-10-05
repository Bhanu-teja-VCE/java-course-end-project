package com.greencorridor.model;

/**
 * Direction of travel of a lane. Screen coordinates are used, so y grows downwards.
 * Traffic keeps to the LEFT, as on Indian roads.
 */
public enum Direction {
    EAST(1, 0, Axis.EW, "Eastbound"),
    WEST(-1, 0, Axis.EW, "Westbound"),
    SOUTH(0, 1, Axis.NS, "Southbound"),
    NORTH(0, -1, Axis.NS, "Northbound");

    private final int dx;
    private final int dy;
    private final Axis axis;
    private final String label;

    Direction(int dx, int dy, Axis axis, String label) {
        this.dx = dx;
        this.dy = dy;
        this.axis = axis;
        this.label = label;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    /** x part of the unit vector pointing to the driver's left. */
    public int getLeftX() {
        return dy;
    }

    /** y part of the unit vector pointing to the driver's left. */
    public int getLeftY() {
        return -dx;
    }

    public double angle() {
        return Math.atan2(dy, dx);
    }

    public Axis getAxis() {
        return axis;
    }

    public String getLabel() {
        return label;
    }
}
