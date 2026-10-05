package com.greencorridor.model;

/** A finished journey of one vehicle across the map. */
public class TripRecord extends SimEvent {

    private static final long serialVersionUID = 1L;

    private final int vehicleNo;
    private final String vehicleType;
    private final boolean emergency;
    private final Direction direction;
    private final long entrySimMs;
    private final long exitSimMs;
    private final double delaySec;
    private final double waitSec;
    private final int stops;

    public TripRecord(int vehicleNo, String vehicleType, boolean emergency, Direction direction,
                      long entrySimMs, long exitSimMs, double delaySec, double waitSec, int stops) {
        this.vehicleNo = vehicleNo;
        this.vehicleType = vehicleType;
        this.emergency = emergency;
        this.direction = direction;
        this.entrySimMs = entrySimMs;
        this.exitSimMs = exitSimMs;
        this.delaySec = delaySec;
        this.waitSec = waitSec;
        this.stops = stops;
    }

    public int getVehicleNo() {
        return vehicleNo;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public boolean isEmergency() {
        return emergency;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getEntrySimMs() {
        return entrySimMs;
    }

    public long getExitSimMs() {
        return exitSimMs;
    }

    public double getTravelSec() {
        return (exitSimMs - entrySimMs) / 1000.0;
    }

    /** Extra time compared with an empty road and no red lights. */
    public double getDelaySec() {
        return delaySec;
    }

    public double getWaitSec() {
        return waitSec;
    }

    public int getStops() {
        return stops;
    }

    @Override
    public String toLogLine() {
        return String.format("TRIP       %-10s #%-4d %-10s in=%-8s out=%-8s travel=%5.1fs delay=%5.1fs wait=%5.1fs stops=%d",
                vehicleType, vehicleNo, direction.getLabel(), seconds(entrySimMs), seconds(exitSimMs),
                getTravelSec(), delaySec, waitSec, stops);
    }
}
