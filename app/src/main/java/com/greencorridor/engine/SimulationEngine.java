package com.greencorridor.engine;

import com.greencorridor.db.RunDao;
import com.greencorridor.exception.IllegalSimulationStateException;
import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.model.PreemptionEvent;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.RunSummary;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;
import com.greencorridor.model.SimEvent;
import com.greencorridor.model.TripRecord;
import com.greencorridor.road.Junction;
import com.greencorridor.road.Lane;
import com.greencorridor.road.RoadNetwork;
import com.greencorridor.vehicle.EmergencyVehicle;
import com.greencorridor.vehicle.Vehicle;
import com.greencorridor.vehicle.VehicleFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs one simulation. It owns the road network and starts these threads:
 *
 * <pre>
 *   Supervisor       ends the run when the scenario's duration is reached   (inner class, extends Thread)
 *   Spawner          creates arriving vehicles                               (extends Thread)
 *   Signal-J1..J3    one traffic light controller per junction              (Runnable)
 *   DB-Logger        stores finished trips and pre-emptions                 (extends Thread)
 *   Car-12, Bus-13.. one thread per vehicle                                  (Runnable)
 * </pre>
 *
 * A new engine is created for every run.
 */
public class SimulationEngine {

    private final Scenario scenario;
    private final SignalMode signalMode;
    private final PriorityMode priorityMode;
    private final Map<String, Integer> vehicleMix;
    private final RoadNetwork network;
    private final SimClock clock = new SimClock();
    private final StatsCollector stats = new StatsCollector();
    private final ThreadRegistry registry = new ThreadRegistry();
    private final EventBuffer<SimEvent> eventBuffer = new EventBuffer<SimEvent>(SimConfig.EVENT_BUFFER_CAPACITY);
    private final List<SignalController> controllers = new ArrayList<SignalController>();
    private final RunDao runDao;                  // null = do not save in MySQL
    private final EngineListener listener;

    private volatile boolean running;
    private volatile boolean finished;
    private int runId = -1;
    private DatabaseLogger logger;
    private volatile VehicleSpawner spawner;

    public SimulationEngine(Scenario scenario, SignalMode signalMode, PriorityMode priorityMode,
                            RunDao runDao, EngineListener listener) throws InvalidScenarioException {
        scenario.validate();
        this.scenario = scenario.copy();
        this.signalMode = signalMode;
        this.priorityMode = priorityMode;
        this.vehicleMix = scenario.parseVehicleMix();
        this.runDao = runDao;
        this.listener = listener;
        this.network = new RoadNetwork(scenario.getJunctionCount());
        Vehicle.resetNumbering();
        for (Junction j : network.getJunctions()) {
            SignalController controller = new SignalController(j, this, this.scenario, signalMode);
            j.setSignal(controller);
            controllers.add(controller);
        }
    }

    /** Starts every thread of the run. */
    public synchronized void start() {
        if (running || finished) {
            throw new IllegalSimulationStateException("A simulation can be started only once");
        }
        running = true;
        if (runDao != null) {
            try {
                runId = runDao.startRun(scenario, signalMode, priorityMode);
                log("Run #" + runId + " is being recorded in MySQL (sp_start_run)");
            } catch (RepositoryException e) {
                log("This run will not be saved: " + e.getMessage());
            }
        }
        log(String.format("Started \"%s\": %s signals, %s, %d junction(s)", scenario.getName(),
                signalMode.getLabel(), priorityMode.getLabel(), scenario.getJunctionCount()));

        logger = new DatabaseLogger(eventBuffer, runId, this);
        launch(logger, logger);
        for (SignalController c : controllers) {
            Thread t = new Thread(c, "Signal-" + c.getJunction().getName());
            t.setPriority(Thread.NORM_PRIORITY + 2);    // signals matter more than single vehicles
            launch(t, c);
        }
        spawner = new VehicleSpawner(this, scenario, vehicleMix);
        spawner.setPriority(Thread.NORM_PRIORITY);
        launch(spawner, spawner);
        Supervisor supervisor = new Supervisor();
        supervisor.setPriority(Thread.NORM_PRIORITY + 1);
        launch(supervisor, supervisor);
    }

    private void launch(Thread thread, Monitorable owner) {
        registry.register(thread, owner);
        thread.start();
    }

    /**
     * Ends the run. Called from the Stop button (event dispatch thread) or from the
     * supervisor when time is up. The slow part runs on a separate "RunFinisher" thread
     * so the window never freezes.
     */
    public void stop(final boolean completed) {
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
            finished = true;
        }
        final long simMs = clock.now();
        clock.setPaused(false);                         // release threads parked at the pause gate
        for (Thread t : registry.threads()) {
            if (t != logger && t != Thread.currentThread()) {
                t.interrupt();                          // wakes threads in sleep() or wait()
            }
        }
        Thread finisher = new Thread(new Runnable() {   // anonymous inner class
            @Override
            public void run() {
                finish(completed, simMs);
            }
        }, "RunFinisher");
        finisher.start();
    }

    private void finish(boolean completed, long simMs) {
        for (Thread t : registry.threads()) {
            if (t != logger && t.isAlive()) {
                try {
                    t.join(2000);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
        try {
            eventBuffer.put(SimEvent.END);              // tell the logger that no more events come
            logger.join(15000);
        } catch (InterruptedException ignored) {
            // give up waiting
        }

        RunSummary summary = buildSummary(completed ? "COMPLETED" : "STOPPED", simMs);
        if (runId > 0 && logger.isDbHealthy()) {
            try {
                runDao.finishRun(runId, summary.getSimSeconds(), summary.getStatus(), summary);
            } catch (RepositoryException e) {
                log("Could not close the run in MySQL: " + e.getMessage());
            }
        }
        log("Run " + summary.getStatus().toLowerCase() + " after " + summary.getSimSeconds() + " simulated seconds");
        if (listener != null) {
            listener.onRunFinished(summary);
        }
    }

    private RunSummary buildSummary(String status, long simMs) {
        StatsCollector.Snapshot s = stats.snapshot(simMs);
        RunSummary r = new RunSummary();
        r.setScenarioName(scenario.getName());
        r.setSignalMode(signalMode);
        r.setPriorityMode(priorityMode);
        r.setStatus(status);
        r.setSimSeconds((int) Math.round(simMs / 1000.0));
        r.setTripsCompleted(s.tripsCompleted);
        r.setVehiclesTurnedAway(s.vehiclesTurnedAway);
        r.setAvgDelaySec(s.avgDelaySec);
        r.setP95DelaySec(s.p95DelaySec);
        r.setMaxDelaySec(s.maxDelaySec);
        r.setAvgStops(s.avgStops);
        r.setThroughputPerMin(s.throughputPerMin);
        r.setEmergencyTrips(s.emergencyTrips);
        r.setEmergencyAvgDelaySec(s.emergencyAvgDelaySec);
        r.setPreemptions(s.preemptions);
        r.setAvgResponseSec(s.avgResponseSec);
        r.setByType(s.byType);
        r.setMostDelayed(s.mostDelayed);
        return r;
    }

    // ----- controls used by the user interface -----

    public void pause() {
        requireRunning();
        clock.setPaused(true);
        log("Paused");
    }

    public void resume() {
        requireRunning();
        clock.setPaused(false);
        log("Resumed");
    }

    public void setSpeed(int speed) {
        clock.setSpeed(speed);
    }

    /**
     * Sends an ambulance or fire engine towards a lane. It reaches the edge of the map
     * {@link SimConfig#APPROACH_MS} later and then jumps the lane's entry queue. With a
     * green corridor the junctions on its route are told straight away.
     */
    public Vehicle dispatchEmergency(String typeCode, Lane lane) {
        requireRunning();
        Vehicle v = VehicleFactory.create(typeCode);
        createThread(v, lane);
        registry.register(v.getThread(), v);
        stats.vehicleCreated();
        long now = clock.now();
        log(String.format("%s dispatched to the %s, arriving in %d s (%s)", v.getLabel(), lane.describe(),
                SimConfig.APPROACH_MS / 1000, priorityMode.getLabel().toLowerCase()));
        if (priorityMode == PriorityMode.CORRIDOR && v instanceof EmergencyVehicle) {
            ((EmergencyVehicle) v).preNotifyRoute(now);
        }
        spawner.approach(v, lane, now + SimConfig.APPROACH_MS);
        return v;
    }

    /** True while a dispatched emergency vehicle is still on its way to the map. */
    public boolean isApproaching(Vehicle vehicle) {
        return spawner != null && spawner.isApproaching(vehicle);
    }

    /** Emergency vehicles on their way to the map edge. */
    public List<Vehicle> getApproachingVehicles() {
        return spawner == null ? new ArrayList<Vehicle>() : spawner.approachingVehicles();
    }

    /** Traffic-police override from a mouse click on a junction. */
    public void forceSwitch(Junction junction) {
        requireRunning();
        if (junction.getSignal().forceSwitch()) {
            log(junction.getName() + ": manual override, the green ends now");
        } else {
            log(junction.getName() + ": manual override refused (lights already changing or held for an emergency)");
        }
    }

    private void requireRunning() {
        if (!running) {
            throw new IllegalSimulationStateException("The simulation is not running");
        }
    }

    // ----- called by simulation threads -----

    /** Creates the thread of an arriving vehicle (state NEW) and queues it at the lane entry. */
    void admit(Vehicle vehicle, Lane lane, boolean priority) {
        createThread(vehicle, lane);
        vehicle.markArrival(clock.now());
        if (lane.enqueue(vehicle, priority)) {
            registry.register(vehicle.getThread(), vehicle);
            stats.vehicleCreated();
        } else {
            stats.vehicleTurnedAway();                  // the queue at the entry is full
        }
    }

    private void createThread(Vehicle vehicle, Lane lane) {
        Thread thread = new Thread(vehicle, vehicle.getDisplayName().replace(' ', '-') + "-" + vehicle.getNumber());
        vehicle.attach(this, lane, thread);
        thread.setPriority(vehicle.getThreadPriority());
    }

    public void vehicleFinished(Vehicle vehicle, TripRecord trip) {
        stats.recordTrip(trip);
        publish(trip);
        if (trip.isEmergency()) {
            log(String.format("%s crossed the map in %.1f s (delay %.1f s, %d stop%s)", vehicle.getLabel(),
                    trip.getTravelSec(), trip.getDelaySec(), trip.getStops(), trip.getStops() == 1 ? "" : "s"));
        }
    }

    public void preemptionFinished(PreemptionEvent event) {
        if (!running) {
            return;                                     // the run is being torn down
        }
        stats.recordPreemption(event);
        publish(event);
    }

    private void publish(SimEvent event) {
        try {
            eventBuffer.put(event);                     // producer side of producer-consumer
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();         // keep the interrupt for the caller's loop
        }
    }

    /** Adds a line to the event log, prefixed with the simulated time. */
    public void log(String message) {
        if (listener != null) {
            long t = clock.now() / 1000;
            listener.onLog(String.format("[%02d:%02d] %s", t / 60, t % 60, message));
        }
    }

    // ----- getters -----

    public boolean isRunning() {
        return running;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isPaused() {
        return clock.isPaused();
    }

    public SimClock getClock() {
        return clock;
    }

    public RoadNetwork getNetwork() {
        return network;
    }

    public StatsCollector getStats() {
        return stats;
    }

    public ThreadRegistry getRegistry() {
        return registry;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public SignalMode getSignalMode() {
        return signalMode;
    }

    public PriorityMode getPriorityMode() {
        return priorityMode;
    }

    public int getRunId() {
        return runId;
    }

    public List<Vehicle> getVehicles() {
        List<Vehicle> all = new ArrayList<Vehicle>();
        for (Lane lane : network.getLanes()) {
            all.addAll(lane.snapshot());
        }
        return all;
    }

    /**
     * Ends the run when the scenario's time is up. An inner (non-static) class, so it can
     * read the engine's fields directly.
     */
    private class Supervisor extends Thread implements Monitorable {

        Supervisor() {
            super("Supervisor");
        }

        @Override
        public void run() {
            long end = scenario.getDurationSec() * 1000L;
            try {
                while (running) {
                    clock.awaitIfPaused();
                    if (clock.now() >= end) {
                        // "stop(true)" alone would mean Thread.stop() of this inner class,
                        // so name the outer object explicitly.
                        SimulationEngine.this.stop(true);
                        return;
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                // stopped early by the user
            }
        }

        @Override
        public String getRole() {
            return "Run supervisor";
        }

        @Override
        public String getStatusText() {
            long left = Math.max(0, scenario.getDurationSec() - clock.now() / 1000);
            return "ends the run in " + left + " s of simulated time";
        }
    }
}
