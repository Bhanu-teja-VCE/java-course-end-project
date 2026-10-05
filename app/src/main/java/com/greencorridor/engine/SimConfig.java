package com.greencorridor.engine;

/**
 * Fixed numbers used by the simulation. Distances are in pixels of the world map
 * (6 px = 1 metre) and times in milliseconds of simulated time unless stated otherwise.
 * The class is final with a private constructor: it only holds constants.
 */
public final class SimConfig {

    private SimConfig() {
    }

    // ----- world map -----
    public static final int WORLD_WIDTH = 1200;
    public static final int WORLD_HEIGHT = 640;
    public static final int ROAD_Y = 320;              // centre line of the main road
    public static final int ROAD_WIDTH = 64;
    public static final int HALF_ROAD = ROAD_WIDTH / 2;
    public static final int LANE_OFFSET = 16;          // lane centre from road centre (keep left)
    public static final int ENTRY_MARGIN = 60;         // lanes start this far outside the map
    public static final double PX_PER_METRE = 6.0;

    // ----- threads -----
    public static final int TICK_MS = 30;              // real milliseconds between two steps of a thread
    public static final long MAX_STEP_MS = 250;        // longest simulated step a vehicle will take

    // ----- driving -----
    public static final double MIN_GAP = 7;            // bumper-to-bumper gap in a queue
    public static final double STOP_MARGIN = 16;       // stop line distance before the junction box
    public static final double STOPPED_SPEED = 3;      // below this a vehicle counts as stopped
    public static final double BRAKE_DECEL = 170;      // px/s^2 used to slow down smoothly
    public static final double YELLOW_COMMIT_SEC = 0.6; // closer than this on yellow -> keep going
    public static final double CRAWL_SPEED = 25;       // leader slower than this -> do not block the box
    public static final long REACTION_MS = 450;        // driver reaction time when the way clears

    // ----- signals -----
    public static final long ALL_RED_MS = 2000;
    public static final long MIN_GREEN_MS = 4000;
    public static final long RECOVERY_GREEN_MS = 3000; // green left after an emergency vehicle passes
    public static final double DEMAND_RANGE = 170;     // detector length in front of a stop line

    // ----- emergency vehicles -----
    public static final double LOCAL_DETECT_RANGE = 110;     // roadside sensor just before the junction
    public static final double CORRIDOR_DETECT_RANGE = 800;  // radio call: covers a full light change
    public static final double YIELD_RANGE = 110;            // pull over when a siren is this close behind
    public static final double YIELD_SHIFT = 10;             // how far a vehicle moves to the left edge
    public static final double EMERGENCY_SHIFT = 4;          // emergency vehicles keep near the centre line
    public static final double LATERAL_SPEED = 45;           // px/s sideways while pulling over
    public static final long APPROACH_MS = 5000;             // time from dispatch to reaching the map edge

    // ----- queues and buffers -----
    public static final int ENTRY_QUEUE_CAP = 12;      // vehicles waiting to enter one lane
    public static final int EVENT_BUFFER_CAPACITY = 100;
    public static final int DB_BATCH_SIZE = 25;
}
