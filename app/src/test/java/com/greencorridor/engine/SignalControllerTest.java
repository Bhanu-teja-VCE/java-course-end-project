package com.greencorridor.engine;

import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.model.Axis;
import com.greencorridor.model.Direction;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;
import com.greencorridor.road.Lane;
import com.greencorridor.vehicle.Ambulance;
import com.greencorridor.vehicle.FireEngine;
import com.greencorridor.vehicle.Vehicle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives a controller's tick() by hand with made-up times; no threads are started. */
class SignalControllerTest {

    private SimulationEngine engine(SignalMode mode) throws InvalidScenarioException {
        Scenario s = new Scenario("Signal test");
        s.setJunctionCount(1);
        s.setMainGreenSec(12);
        s.setCrossGreenSec(8);
        s.setYellowSec(3);
        return new SimulationEngine(s, mode, PriorityMode.CORRIDOR, null, null);
    }

    private static Vehicle attach(Vehicle v, SimulationEngine e, Lane lane) {
        v.attach(e, lane, new Thread(v));
        return v;
    }

    @Test
    void fixedTimeCycle() throws Exception {
        SignalController c = engine(SignalMode.FIXED).getNetwork().getJunctions().get(0).getSignal();
        c.tick(0, 5, 5);
        assertEquals(Phase.EW_GREEN, c.getPhase());
        c.tick(11999, 5, 5);
        assertEquals(Phase.EW_GREEN, c.getPhase());
        c.tick(12000, 5, 5);
        assertEquals(Phase.EW_YELLOW, c.getPhase());
        c.tick(15000, 5, 5);
        assertEquals(Phase.EW_CLEARANCE, c.getPhase());
        assertEquals(SignalLight.RED, c.getLight(Axis.EW));
        assertEquals(SignalLight.RED, c.getLight(Axis.NS));
        c.tick(17000, 5, 5);
        assertEquals(Phase.NS_GREEN, c.getPhase());
        assertTrue(c.isGreen(Axis.NS));
        c.tick(25000, 5, 5);
        assertEquals(Phase.NS_YELLOW, c.getPhase());
    }

    @Test
    void adaptiveGapsOutAndRestsInGreen() throws Exception {
        SignalController c = engine(SignalMode.ADAPTIVE).getNetwork().getJunctions().get(0).getSignal();
        c.tick(5000, 0, 0);
        assertEquals(Phase.EW_GREEN, c.getPhase(), "nobody waiting anywhere: stay green");
        c.tick(30000, 3, 0);
        assertEquals(Phase.EW_GREEN, c.getPhase(), "nobody on the cross street: stay green");
        c.tick(31000, 0, 2);
        assertEquals(Phase.EW_YELLOW, c.getPhase(), "main road empty and cross street waiting: gap out");
    }

    @Test
    void ambulanceGetsGreenAndHoldsIt() throws Exception {
        SimulationEngine e = engine(SignalMode.FIXED);
        SignalController c = e.getNetwork().getJunctions().get(0).getSignal();
        Lane east = e.getNetwork().getMainLane(Direction.EAST);
        Vehicle ambulance = attach(new Ambulance(), e, east);

        // run the normal cycle until the cross street is green
        c.tick(0, 1, 1);
        c.tick(12000, 1, 1);
        c.tick(15000, 1, 1);
        c.tick(17000, 1, 1);
        assertEquals(Phase.NS_GREEN, c.getPhase());

        c.requestPreemption(ambulance, Axis.EW, 18000);
        c.tick(18000, 1, 1);
        assertEquals(Phase.NS_YELLOW, c.getPhase(), "the cross street's green is cut short");
        c.tick(21000, 1, 1);
        assertEquals(Phase.NS_CLEARANCE, c.getPhase());
        c.tick(23000, 1, 1);
        assertEquals(Phase.EW_GREEN, c.getPhase());
        c.tick(60000, 1, 9);
        assertEquals(Phase.EW_GREEN, c.getPhase(), "green is held while the ambulance comes");
        assertEquals(Axis.EW, c.getPriorityAxis());

        c.releasePreemption(ambulance, 61000);
        c.tick(61000, 1, 9);
        assertEquals(Phase.EW_GREEN, c.getPhase(), "a short recovery green first");
        c.tick(64000, 1, 9);
        assertEquals(Phase.EW_YELLOW, c.getPhase(), "then the cross street gets its turn");
        assertEquals(null, c.getPriorityAxis());
    }

    @Test
    void starvedRoadGetsItsMinimumGreen() throws Exception {
        SimulationEngine e = engine(SignalMode.FIXED);
        SignalController c = e.getNetwork().getJunctions().get(0).getSignal();
        Lane east = e.getNetwork().getMainLane(Direction.EAST);
        Vehicle first = attach(new Ambulance(), e, east);
        Vehicle second = attach(new Ambulance(), e, east);

        c.tick(0, 1, 1);
        c.requestPreemption(first, Axis.EW, 1000);     // main road is green: held
        c.tick(1000, 1, 9);
        c.releasePreemption(first, 20000);
        c.tick(23000, 1, 9);                           // recovery green over
        c.tick(26000, 1, 9);                           // yellow over
        c.tick(28000, 1, 9);                           // all red over
        assertEquals(Phase.NS_GREEN, c.getPhase(), "the cross street finally gets green");

        c.requestPreemption(second, Axis.EW, 29000);   // next ambulance, straight away
        c.tick(29000, 1, 9);
        assertEquals(Phase.NS_GREEN, c.getPhase(), "the starved cross street keeps its minimum green");
        c.tick(32000, 1, 9);
        assertEquals(Phase.NS_YELLOW, c.getPhase(), "after 4 s the emergency takes over");
    }

    @Test
    void moreUrgentRequestIsServedFirst() throws Exception {
        SimulationEngine e = engine(SignalMode.FIXED);
        SignalController c = e.getNetwork().getJunctions().get(0).getSignal();
        Vehicle fire = attach(new FireEngine(), e, e.getNetwork().getCrossLane(Direction.SOUTH, 0));
        Vehicle ambulance = attach(new Ambulance(), e, e.getNetwork().getMainLane(Direction.EAST));
        c.requestPreemption(fire, Axis.NS, 0);
        c.requestPreemption(ambulance, Axis.EW, 100);
        assertEquals(ambulance.getLabel(), c.getPriorityHolder(), "medical emergency before fire call");
        c.releasePreemption(ambulance, 200);
        assertEquals(fire.getLabel(), c.getPriorityHolder());
    }

    @Test
    void manualOverrideEndsGreen() throws Exception {
        SignalController c = engine(SignalMode.FIXED).getNetwork().getJunctions().get(0).getSignal();
        c.tick(0, 1, 1);
        assertTrue(c.forceSwitch());
        c.tick(1000, 1, 1);
        assertEquals(Phase.EW_YELLOW, c.getPhase());
        assertFalse(c.forceSwitch(), "only a green can be ended early");
    }
}
