package com.greencorridor.engine;

import com.greencorridor.exception.IllegalSimulationStateException;
import com.greencorridor.model.Direction;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.RunSummary;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;
import com.greencorridor.tools.SafetyChecker;
import com.greencorridor.vehicle.VehicleFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs a short simulation with real threads and checks that nothing impossible happens. */
class SimulationEngineTest {

    @Test
    void shortRunIsSafeAndFinishes() throws Exception {
        Scenario s = Scenario.builtInDefaults().get(4).copy();   // Hospital Road: 2 junctions, frequent emergencies
        s.setDurationSec(60);
        final CountDownLatch done = new CountDownLatch(1);
        final RunSummary[] result = new RunSummary[1];
        SimulationEngine engine = new SimulationEngine(s, SignalMode.FIXED, PriorityMode.CORRIDOR, null,
                new EngineListener() {
                    @Override
                    public void onLog(String message) {
                    }

                    @Override
                    public void onRunFinished(RunSummary summary) {
                        result[0] = summary;
                        done.countDown();
                    }
                });
        engine.setSpeed(8);
        engine.start();
        assertThrows(IllegalSimulationStateException.class, engine::start, "a run starts only once");
        SafetyChecker checker = new SafetyChecker(engine);
        checker.start();
        Thread.sleep(500);
        engine.dispatchEmergency(VehicleFactory.AMBULANCE, engine.getNetwork().getMainLane(Direction.EAST));

        assertTrue(done.await(40, TimeUnit.SECONDS), "the supervisor ends the run after 60 simulated seconds");
        RunSummary summary = result[0];
        assertEquals("COMPLETED", summary.getStatus());
        assertTrue(summary.getTripsCompleted() > 20, "trips: " + summary.getTripsCompleted());
        assertTrue(summary.getEmergencyTrips() >= 1, "the ambulance made it across");
        assertTrue(summary.getPreemptions() >= 2, "it was given green at both junctions");
        assertEquals(0, checker.getOverlaps(), checker.getFirstProblem());
        assertEquals(0, checker.getBoxConflicts(), checker.getFirstProblem());
        assertTrue(checker.getSamples() > 50);

        Thread.sleep(300);
        assertEquals(0, engine.getRegistry().liveCount(), "every thread has ended");
    }
}
