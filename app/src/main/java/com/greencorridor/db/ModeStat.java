package com.greencorridor.db;

import com.greencorridor.model.PriorityMode;

/** One row of the stored procedure sp_mode_comparison. */
public class ModeStat {

    private final PriorityMode mode;
    private final int runs;
    private final Double avgDelaySec;
    private final Double emergencyAvgDelaySec;
    private final Double throughputPerMin;

    public ModeStat(PriorityMode mode, int runs, Double avgDelaySec, Double emergencyAvgDelaySec,
                    Double throughputPerMin) {
        this.mode = mode;
        this.runs = runs;
        this.avgDelaySec = avgDelaySec;
        this.emergencyAvgDelaySec = emergencyAvgDelaySec;
        this.throughputPerMin = throughputPerMin;
    }

    public PriorityMode getMode() {
        return mode;
    }

    public int getRuns() {
        return runs;
    }

    /** Average delay of ordinary vehicles, or null if unknown. */
    public Double getAvgDelaySec() {
        return avgDelaySec;
    }

    /** Average delay of emergency vehicles, or null if none finished. */
    public Double getEmergencyAvgDelaySec() {
        return emergencyAvgDelaySec;
    }

    public Double getThroughputPerMin() {
        return throughputPerMin;
    }
}
