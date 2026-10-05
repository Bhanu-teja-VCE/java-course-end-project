package com.greencorridor.engine;

import com.greencorridor.model.Direction;
import com.greencorridor.model.Scenario;
import com.greencorridor.road.Lane;
import com.greencorridor.road.RoadNetwork;
import com.greencorridor.vehicle.Vehicle;
import com.greencorridor.vehicle.VehicleFactory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

/**
 * Generates traffic (extends Thread). Every tick each lane gets a new arrival with a
 * probability that matches the scenario's vehicles-per-minute, which gives random
 * (Poisson) arrivals like real traffic. Arrived vehicles wait in the lane's entry queue
 * as NEW threads; the spawner starts a thread once its vehicle fits on the road.
 * Dispatched emergency vehicles first spend {@link SimConfig#APPROACH_MS} approaching
 * the map before they join the front of the entry queue.
 */
public class VehicleSpawner extends Thread implements Monitorable {

    private final SimulationEngine engine;
    private final Scenario scenario;
    private final Map<String, Integer> mix;
    private final Random random;
    private volatile int arrivals;
    private volatile int emergencies;
    /** Emergency vehicles on their way to the edge of the map. */
    private final LinkedList<Pending> approaching = new LinkedList<Pending>();

    public VehicleSpawner(SimulationEngine engine, Scenario scenario, Map<String, Integer> mix) {
        super("Spawner");
        this.engine = engine;
        this.scenario = scenario;
        this.mix = mix;
        this.random = new Random(scenario.getRandomSeed());
    }

    @Override
    public void run() {
        SimClock clock = engine.getClock();
        RoadNetwork network = engine.getNetwork();
        long last = clock.now();
        long every = scenario.getEmergencyEverySec() * 1000L;
        long nextEmergency = every > 0 ? jitter(every) / 2 : Long.MAX_VALUE;
        try {
            while (engine.isRunning()) {
                clock.awaitIfPaused();
                long now = clock.now();
                double dt = Math.min(now - last, SimConfig.MAX_STEP_MS) / 1000.0;
                last = now;

                for (Lane lane : network.getLanes()) {
                    int perMinute = lane.isMainRoad() ? scenario.getMainRatePerMin() : scenario.getCrossRatePerMin();
                    if (random.nextDouble() < perMinute / 60.0 * dt) {
                        arrivals++;
                        engine.admit(VehicleFactory.randomFromMix(mix, random), lane, false);
                    }
                }

                if (now >= nextEmergency) {
                    dispatchScheduledEmergency(network);
                    nextEmergency += jitter(every);
                }
                releaseArrivedEmergencies(now);

                // Start the thread of every vehicle that now fits on its lane.
                for (Lane lane : network.getLanes()) {
                    Vehicle v = lane.admitNext();
                    if (v != null) {
                        v.getThread().start();           // NEW -> RUNNABLE
                    }
                }
                Thread.sleep(SimConfig.TICK_MS);
            }
        } catch (InterruptedException e) {
            // run stopped
        }
    }

    /**
     * Emergencies do not come like clockwork: the gap is random, between half and one
     * and a half times the scenario's interval (same average).
     */
    private long jitter(long every) {
        return (long) (every * (0.5 + random.nextDouble()));
    }

    /** Ambulances head east to the hospital; every third call is a fire engine on a cross street. */
    private void dispatchScheduledEmergency(RoadNetwork network) {
        emergencies++;
        if (emergencies % 3 == 0) {
            int junction = random.nextInt(network.getJunctions().size());
            engine.dispatchEmergency(VehicleFactory.FIRE_ENGINE, network.getCrossLane(Direction.SOUTH, junction));
        } else {
            engine.dispatchEmergency(VehicleFactory.AMBULANCE, network.getMainLane(Direction.EAST));
        }
    }

    /** Schedules an emergency vehicle to reach the edge of the map at "arrivesAt". */
    synchronized void approach(Vehicle vehicle, Lane lane, long arrivesAt) {
        approaching.add(new Pending(vehicle, lane, arrivesAt));
    }

    private synchronized void releaseArrivedEmergencies(long now) {
        Iterator<Pending> it = approaching.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (now >= p.arrivesAt) {
                it.remove();
                p.vehicle.markArrival(p.arrivesAt);
                p.lane.enqueue(p.vehicle, true);     // jumps the entry queue
            }
        }
    }

    /** True while the vehicle is still approaching the map. */
    synchronized boolean isApproaching(Vehicle vehicle) {
        for (Pending p : approaching) {
            if (p.vehicle == vehicle) {
                return true;
            }
        }
        return false;
    }

    /** Emergency vehicles still approaching, for the canvas to show at the map edge. */
    public synchronized java.util.List<Vehicle> approachingVehicles() {
        java.util.List<Vehicle> list = new java.util.ArrayList<Vehicle>();
        for (Pending p : approaching) {
            list.add(p.vehicle);
        }
        return list;
    }

    private static class Pending {
        final Vehicle vehicle;
        final Lane lane;
        final long arrivesAt;

        Pending(Vehicle vehicle, Lane lane, long arrivesAt) {
            this.vehicle = vehicle;
            this.lane = lane;
            this.arrivesAt = arrivesAt;
        }
    }

    @Override
    public String getRole() {
        return "Traffic generator";
    }

    @Override
    public String getStatusText() {
        return arrivals + " arrivals, " + emergencies + " scheduled emergencies";
    }
}
