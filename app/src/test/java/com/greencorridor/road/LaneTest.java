package com.greencorridor.road;

import com.greencorridor.engine.SimConfig;
import com.greencorridor.model.Direction;
import com.greencorridor.vehicle.Ambulance;
import com.greencorridor.vehicle.Car;
import com.greencorridor.vehicle.Vehicle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneTest {

    @Test
    void networkGeometry() {
        RoadNetwork net = new RoadNetwork(3);
        assertEquals(3, net.getJunctions().size());
        assertEquals(2 + 2 * 3, net.getLanes().size());
        Lane east = net.getMainLane(Direction.EAST);
        assertEquals(3, east.getStopLines().size(), "the main road crosses every junction");
        assertEquals(1, net.getCrossLane(Direction.NORTH, 1).getStopLines().size());
        StopLine first = east.getStopLines().get(0);
        // J1 at x=300: box from 268 to 332, lane starts 60 px off the map
        assertEquals(300 - 32 - SimConfig.STOP_MARGIN + 60, first.getStopS(), 0.001);
        assertEquals(300 + 32 + 60, first.getBoxExitS(), 0.001);
        assertSame(first, east.nextStopLine(0));
        assertSame(east.getStopLines().get(1), east.nextStopLine(first.getStopS() + 1));
    }

    @Test
    void entryQueueOrderAndCapacity() {
        Lane lane = new RoadNetwork(1).getMainLane(Direction.EAST);
        for (int i = 0; i < SimConfig.ENTRY_QUEUE_CAP; i++) {
            assertTrue(lane.enqueue(new Car(), false));
        }
        assertFalse(lane.enqueue(new Car(), false), "a full entry queue turns ordinary vehicles away");
        Vehicle ambulance = new Ambulance();
        assertTrue(lane.enqueue(ambulance, true), "emergency vehicles are never turned away");
        assertSame(ambulance, lane.admitNext(), "and they enter first");
        assertNull(lane.admitNext(), "the start of the lane is now occupied");
    }

    @Test
    void leaderIsTheVehicleAhead() {
        Lane lane = new RoadNetwork(1).getMainLane(Direction.EAST);
        Car first = new Car();
        lane.enqueue(first, false);
        assertSame(first, lane.admitNext());
        assertNull(lane.leaderOf(first));
        assertEquals(1, lane.vehicleCount());
    }

    @Test
    void junctionBoxIsMutuallyExclusive() {
        Junction j = new RoadNetwork(1).getJunctions().get(0);
        assertTrue(j.tryEnterBox(com.greencorridor.model.Axis.EW));
        assertTrue(j.tryEnterBox(com.greencorridor.model.Axis.EW), "same direction may share the box");
        assertFalse(j.tryEnterBox(com.greencorridor.model.Axis.NS), "crossing traffic must wait");
        j.exitBox(com.greencorridor.model.Axis.EW);
        j.exitBox(com.greencorridor.model.Axis.EW);
        assertTrue(j.tryEnterBox(com.greencorridor.model.Axis.NS));
    }
}
