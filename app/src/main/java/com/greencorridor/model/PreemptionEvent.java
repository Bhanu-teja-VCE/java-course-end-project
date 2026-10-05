package com.greencorridor.model;

/** A junction gave (or tried to give) right of way to an emergency vehicle. */
public class PreemptionEvent extends SimEvent {

    private static final long serialVersionUID = 1L;

    private final int junctionNo;
    private final int vehicleNo;
    private final String vehicleType;
    private final Direction direction;
    private final long requestedSimMs;
    private final long greenSimMs;
    private final long clearedSimMs;

    public PreemptionEvent(int junctionNo, int vehicleNo, String vehicleType, Direction direction,
                           long requestedSimMs, long greenSimMs, long clearedSimMs) {
        this.junctionNo = junctionNo;
        this.vehicleNo = vehicleNo;
        this.vehicleType = vehicleType;
        this.direction = direction;
        this.requestedSimMs = requestedSimMs;
        this.greenSimMs = greenSimMs;
        this.clearedSimMs = clearedSimMs;
    }

    public int getJunctionNo() {
        return junctionNo;
    }

    public int getVehicleNo() {
        return vehicleNo;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getRequestedSimMs() {
        return requestedSimMs;
    }

    public long getGreenSimMs() {
        return greenSimMs;
    }

    public long getClearedSimMs() {
        return clearedSimMs;
    }

    /** Seconds from the request until the vehicle's road showed green. */
    public double getResponseSec() {
        long green = greenSimMs < 0 ? clearedSimMs : greenSimMs;
        return Math.max(0, green - requestedSimMs) / 1000.0;
    }

    @Override
    public String toLogLine() {
        return String.format("PRE-EMPT   J%d %-10s #%-4d %-10s requested=%-8s green=%-8s cleared=%-8s response=%.1fs",
                junctionNo, vehicleType, vehicleNo, direction.getLabel(), seconds(requestedSimMs),
                greenSimMs < 0 ? "-" : seconds(greenSimMs), seconds(clearedSimMs), getResponseSec());
    }
}
