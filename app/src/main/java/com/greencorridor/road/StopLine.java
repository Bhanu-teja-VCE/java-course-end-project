package com.greencorridor.road;

import com.greencorridor.model.Axis;

/** Where a lane meets a junction: the stop line and the far edge of the junction box. */
public class StopLine {

    private final Lane lane;
    private final Junction junction;
    private final double stopS;       // position of the stop line along the lane
    private final double boxEntryS;   // near edge of the junction box
    private final double boxExitS;    // far edge of the junction box

    StopLine(Lane lane, Junction junction, double stopS, double boxEntryS, double boxExitS) {
        this.lane = lane;
        this.junction = junction;
        this.stopS = stopS;
        this.boxEntryS = boxEntryS;
        this.boxExitS = boxExitS;
    }

    public Lane getLane() {
        return lane;
    }

    public Junction getJunction() {
        return junction;
    }

    public Axis getAxis() {
        return lane.getDirection().getAxis();
    }

    public double getStopS() {
        return stopS;
    }

    public double getBoxEntryS() {
        return boxEntryS;
    }

    public double getBoxExitS() {
        return boxExitS;
    }
}
