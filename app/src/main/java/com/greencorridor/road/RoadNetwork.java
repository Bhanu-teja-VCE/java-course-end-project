package com.greencorridor.road;

import com.greencorridor.engine.SimConfig;
import com.greencorridor.model.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The map: one east-west main road crossed by 1 to 3 north-south streets.
 * Each road has one lane per direction and traffic keeps left.
 */
public class RoadNetwork {

    private final List<Junction> junctions = new ArrayList<Junction>();
    private final List<Lane> lanes = new ArrayList<Lane>();
    private final Map<String, Lane> laneByKey = new HashMap<String, Lane>();

    public RoadNetwork(int junctionCount) {
        if (junctionCount < 1 || junctionCount > 3) {
            throw new IllegalArgumentException("junctionCount must be 1, 2 or 3");
        }
        int w = SimConfig.WORLD_WIDTH;
        int h = SimConfig.WORLD_HEIGHT;
        int m = SimConfig.ENTRY_MARGIN;
        int roadY = SimConfig.ROAD_Y;
        int off = SimConfig.LANE_OFFSET;

        for (int i = 0; i < junctionCount; i++) {
            double jx = (double) w * (i + 1) / (junctionCount + 1);
            junctions.add(new Junction(i, Math.round(jx), roadY));
        }

        int id = 0;
        // Main road. Keep left: eastbound traffic uses the northern half.
        addLane("EAST", new Lane(id++, Direction.EAST, -m, roadY - off, w + 2 * m, null));
        addLane("WEST", new Lane(id++, Direction.WEST, w + m, roadY + off, w + 2 * m, null));
        // Cross streets. Southbound traffic uses the eastern half.
        for (Junction j : junctions) {
            addLane("SOUTH@" + j.getIndex(),
                    new Lane(id++, Direction.SOUTH, j.getX() + off, -m, h + 2 * m, j));
            addLane("NORTH@" + j.getIndex(),
                    new Lane(id++, Direction.NORTH, j.getX() - off, h + m, h + 2 * m, j));
        }

        for (Lane lane : lanes) {
            for (Junction j : junctions) {
                if (lane.isMainRoad() || lane.getCrossJunction() == j) {
                    j.addApproach(lane.addStopLine(j));
                }
            }
        }
    }

    private void addLane(String key, Lane lane) {
        lanes.add(lane);
        laneByKey.put(key, lane);
    }

    public List<Junction> getJunctions() {
        return Collections.unmodifiableList(junctions);
    }

    public List<Lane> getLanes() {
        return Collections.unmodifiableList(lanes);
    }

    public Lane getMainLane(Direction direction) {
        return laneByKey.get(direction.name());
    }

    public Lane getCrossLane(Direction direction, int junctionIndex) {
        return laneByKey.get(direction.name() + "@" + junctionIndex);
    }

    /** The lane whose entry point is within "radius" pixels of (x, y), or null. */
    public Lane entryLaneNear(double x, double y, double radius) {
        Lane best = null;
        double bestDist = radius;
        for (Lane lane : lanes) {
            double d = Math.hypot(lane.getEntryX() - x, lane.getEntryY() - y);
            if (d <= bestDist) {
                bestDist = d;
                best = lane;
            }
        }
        return best;
    }

    /** The junction whose box (plus a margin) contains (x, y), or null. */
    public Junction junctionAt(double x, double y, double margin) {
        double half = SimConfig.HALF_ROAD + margin;
        for (Junction j : junctions) {
            if (Math.abs(j.getX() - x) <= half && Math.abs(j.getY() - y) <= half) {
                return j;
            }
        }
        return null;
    }
}
