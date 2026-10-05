package com.greencorridor.tools;

import com.greencorridor.db.RunDao;
import com.greencorridor.engine.EngineListener;
import com.greencorridor.engine.SimulationEngine;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.RunSummary;
import com.greencorridor.model.Scenario;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Runs scenarios without a window, as fast as is sensible, and prints a comparison.
 * Usage: Experiment [scenarioIndex] [speed] [repeats] [db] [verbose]
 * Each run also checks that no vehicles overlap and no crossing traffic shares a box.
 */
public class Experiment {

    public static void main(String[] args) throws Exception {
        int index = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        int speed = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        int repeats = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        boolean useDb = args.length > 3 && args[3].equals("db");
        final boolean verbose = args.length > 4 && args[4].equals("verbose");
        String only = args.length > 5 ? args[5] : null;

        List<Scenario> all = Scenario.builtInDefaults();
        Scenario base = all.get(index);
        System.out.println("Scenario: " + base.getName() + " | speed x" + speed + " | db=" + useDb);
        System.out.printf("%-14s %-4s %6s %8s %8s %6s %8s %8s %6s %7s %6s %6s%n", "mode", "rep", "trips", "avgDelay",
                "p95", "emerg", "emDelay", "resp", "away", "overlap", "box", "stall");
        for (PriorityMode mode : PriorityMode.values()) {
            if (only != null && !mode.name().equals(only)) {
                continue;
            }
            for (int r = 0; r < repeats; r++) {
                Scenario s = base.copy();
                s.setRandomSeed(base.getRandomSeed() + r);
                final CountDownLatch done = new CountDownLatch(1);
                final RunSummary[] result = new RunSummary[1];
                SimulationEngine engine = new SimulationEngine(s, s.getSignalMode(), mode,
                        useDb ? new RunDao() : null, new EngineListener() {
                            @Override
                            public void onLog(String message) {
                                if (verbose) {
                                    System.out.println("   " + message);
                                }
                            }

                            @Override
                            public void onRunFinished(RunSummary summary) {
                                result[0] = summary;
                                done.countDown();
                            }
                        });
                engine.setSpeed(speed);
                engine.start();
                SafetyChecker checker = new SafetyChecker(engine);
                checker.setVerbose(verbose);
                checker.start();
                done.await();
                checker.interrupt();
                RunSummary x = result[0];
                System.out.printf("%-14s %-4d %6d %8.2f %8.2f %6d %8.2f %8.2f %6d %7d %6d %6d %s%n", mode, r,
                        x.getTripsCompleted(), x.getAvgDelaySec(), x.getP95DelaySec(), x.getEmergencyTrips(),
                        x.getEmergencyAvgDelaySec(), x.getAvgResponseSec(), x.getVehiclesTurnedAway(),
                        checker.getOverlaps(), checker.getBoxConflicts(), checker.getStalls(),
                        checker.getFirstProblem() == null ? "" : checker.getFirstProblem());
            }
        }
    }
}
