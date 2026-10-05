package com.greencorridor.tools;

import com.greencorridor.engine.SimConfig;
import com.greencorridor.engine.SimulationEngine;
import com.greencorridor.model.Axis;
import com.greencorridor.road.Junction;
import com.greencorridor.road.Lane;
import com.greencorridor.vehicle.Vehicle;

import java.util.List;

/**
 * Watches a running engine and counts physical impossibilities:
 * vehicles overlapping in a lane, and crossing vehicles inside the same junction box.
 */
public class SafetyChecker extends Thread {

    private final SimulationEngine engine;
    private volatile boolean verbose;
    private volatile int stalls;
    private volatile int overlaps;
    private volatile int boxConflicts;
    private volatile int samples;
    private volatile String firstProblem;
    private int lastTrips = -1;
    private long lastProgressSim;
    private boolean dumped;
    private final java.util.Map<Vehicle, Long> stuckSince = new java.util.HashMap<Vehicle, Long>();

    public SafetyChecker(SimulationEngine engine) {
        super("SafetyChecker");
        this.engine = engine;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (!engine.isFinished() || engine.isRunning()) {
            check();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void check() {
        samples++;
        int trips = engine.getStats().snapshot(1).tripsCompleted;
        long now = engine.getClock().now();
        if (trips != lastTrips) {
            lastTrips = trips;
            lastProgressSim = now;
        } else if (!dumped && engine.isRunning() && now - lastProgressSim > 25000) {
            dumped = true;
            dumpState(now);
        }
        for (Lane lane : engine.getNetwork().getLanes()) {
            List<Vehicle> vs = lane.snapshot();
            if (vs.isEmpty()) {
                continue;
            }
            Vehicle head = vs.get(0);
            Long since = stuckSince.get(head);
            if (head.getSpeed() > 1) {
                stuckSince.remove(head);
            } else if (since == null) {
                stuckSince.put(head, now);
            } else if (!dumped && engine.isRunning() && now - since > 40000) {
                dumped = true;
                System.out.println("Head of " + lane.describe() + " (" + head.getLabel() + ") stopped for 40 s");
                dumpState(now);
            }
        }
        for (Lane lane : engine.getNetwork().getLanes()) {
            List<Vehicle> vs = lane.snapshot();
            for (int i = 1; i < vs.size(); i++) {
                Vehicle ahead = vs.get(i - 1);
                Vehicle behind = vs.get(i);
                if (ahead.isYielding() || behind.isYielding()) {
                    continue;                          // side by side while an emergency vehicle passes
                }
                // Read the follower first: the leader can only move forward afterwards, so a
                // later read of its rear can never create a false overlap.
                double behindFront = behind.getFrontS();
                double aheadRear = ahead.getRearS();
                if (behindFront > aheadRear + 1 && !ahead.isYielding() && !behind.isYielding()) {
                    overlaps++;
                    if (firstProblem == null) {
                        StringBuilder sb = new StringBuilder("OVERLAP at sim " + engine.getClock().now() + " in " + lane.describe() + ":\n");
                        for (Vehicle x : vs) {
                            sb.append(String.format("   %s s=%.1f rear=%.1f v=%.1f Y=%s W=%s %s%n", x.getLabel(), x.getFrontS(),
                                    x.getRearS(), x.getSpeed(), x.isYielding(), x.isWaitingAtSignal(), x.getStatusText()));
                        }
                        System.out.println(sb);
                        firstProblem = String.format("overlap in %s: %s front %.1f > %s rear %.1f", lane.describe(),
                                behind.getLabel(), behindFront, ahead.getLabel(), aheadRear);
                    }
                }
            }
        }
        for (Junction j : engine.getNetwork().getJunctions()) {
            boolean ew = false;
            boolean ns = false;
            for (Lane lane : engine.getNetwork().getLanes()) {
                for (Vehicle v : lane.snapshot()) {
                    if (insideBox(v, j)) {
                        if (lane.getDirection().getAxis() == Axis.EW) {
                            ew = true;
                        } else {
                            ns = true;
                        }
                    }
                }
            }
            if (ew && ns) {
                boxConflicts++;
                if (firstProblem == null) {
                    firstProblem = "both axes inside " + j.getName();
                }
            }
        }
    }

    /** Print a full dump of the lanes and signals when a stall is noticed. */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /** How many times a lane head stood still for 40 s, or no trip finished for 25 s. */
    public int getStalls() {
        return stalls;
    }

    private void dumpState(long now) {
        stalls++;
        if (!verbose) {
            return;
        }
        StringBuilder sb = new StringBuilder("\n===== STALL: no trip finished for 25 s (sim " + now / 1000 + " s) =====\n");
        for (Junction j : engine.getNetwork().getJunctions()) {
            sb.append(j.getName()).append(' ').append(j.getSignal().getStatusText())
              .append(" inBox=").append(j.vehiclesInBox()).append('\n');
        }
        for (Lane lane : engine.getNetwork().getLanes()) {
            sb.append(lane.describe()).append(" queue=").append(lane.entryQueueSize()).append(": ");
            for (Vehicle v : lane.snapshot()) {
                sb.append(String.format("%s[s=%.0f v=%.0f %s%s%s%s] ", v.getLabel(), v.getFrontS(), v.getSpeed(),
                        v.getThread().getState(), v.isYielding() ? " Y" : "", v.isWaitingAtSignal() ? " W" : "",
                        v.getBoxJunctionName() == null ? "" : " BOX:" + v.getBoxJunctionName()));
            }
            sb.append('\n');
        }
        for (java.util.Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            Thread t = e.getKey();
            if (t.getState() == Thread.State.BLOCKED || t.getName().startsWith("Signal")) {
                sb.append("-- ").append(t.getName()).append(' ').append(t.getState()).append('\n');
                for (StackTraceElement el : e.getValue()) {
                    sb.append("     at ").append(el).append('\n');
                }
            }
        }
        System.out.println(sb);
    }

    /** True if any part of the vehicle body lies inside the junction square (shrunk 3 px). */
    private static boolean insideBox(Vehicle v, Junction j) {
        Lane lane = v.getLane();
        double centreS = lane.positionOf(j.getX(), j.getY());
        double half = SimConfig.HALF_ROAD - 3;
        boolean crossesThisJunction = lane.isMainRoad() || lane.getCrossJunction() == j;
        return crossesThisJunction && v.getFrontS() > centreS - half && v.getRearS() < centreS + half;
    }

    public int getOverlaps() {
        return overlaps;
    }

    public int getBoxConflicts() {
        return boxConflicts;
    }

    public int getSamples() {
        return samples;
    }

    public String getFirstProblem() {
        return firstProblem;
    }
}
