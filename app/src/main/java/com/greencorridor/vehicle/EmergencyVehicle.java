package com.greencorridor.vehicle;

import com.greencorridor.engine.SignalController;
import com.greencorridor.engine.SimConfig;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.road.StopLine;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.HashSet;
import java.util.Set;

/**
 * Common behaviour of ambulances and fire engines.
 *
 * <ul>
 *   <li>Their threads run at {@link Thread#MAX_PRIORITY}.</li>
 *   <li>They join the front of a lane's entry queue.</li>
 *   <li>They overtake vehicles that pulled over for them.</li>
 *   <li>Depending on the run's {@link PriorityMode} they ask junctions ahead for a green
 *       light ({@link #beforeStep}) and release it once they are through
 *       ({@link #onLeaveJunction}).</li>
 * </ul>
 */
public abstract class EmergencyVehicle extends Vehicle implements EmergencyResponder {

    private static final Color BEACON_RED = new Color(0xFF1744);
    private static final Color BEACON_BLUE = new Color(0x2979FF);

    /** Junctions this vehicle has asked for priority and not yet released. */
    private final Set<StopLine> requested = new HashSet<StopLine>();

    protected EmergencyVehicle(double length, double width, double maxSpeed, double acceleration) {
        super(length, width, maxSpeed, acceleration);
    }

    @Override
    public final int getThreadPriority() {
        return Thread.MAX_PRIORITY;
    }

    @Override
    public final boolean isEmergency() {
        return true;
    }

    @Override
    protected void beforeStep(long now) {
        // Drivers ahead pull over for the siren. Those blocked in wait() at a red light
        // are woken so they can move aside too; then we pass the ones that have.
        for (Vehicle ahead : lane.vehiclesAhead(this, SimConfig.YIELD_RANGE)) {
            SignalController signal = ahead.getWaitingOn();
            if (signal != null) {
                signal.wakeWaitingVehicles();
            }
        }
        lane.overtakeYielding(this);

        PriorityMode mode = engine.getPriorityMode();
        if (mode == PriorityMode.NONE) {
            return;
        }
        double range = mode == PriorityMode.LOCAL
                ? SimConfig.LOCAL_DETECT_RANGE : SimConfig.CORRIDOR_DETECT_RANGE;
        for (StopLine sl : lane.getStopLines()) {
            double dist = sl.getStopS() - getFrontS();
            // Set.add() returns false if we already asked this junction.
            if (dist >= -1 && dist <= range && requested.add(sl)) {
                sl.getJunction().getSignal().requestPreemption(this, sl.getAxis(), now);
            }
        }
    }

    /**
     * Green corridor: the control room tells every junction on the route as soon as the
     * vehicle is dispatched, while it is still approaching the map. Called before the
     * vehicle's thread starts.
     */
    public void preNotifyRoute(long now) {
        for (StopLine sl : lane.getStopLines()) {
            if (sl.getStopS() <= SimConfig.CORRIDOR_DETECT_RANGE && requested.add(sl)) {
                sl.getJunction().getSignal().requestPreemption(this, sl.getAxis(), now);
            }
        }
    }

    @Override
    protected void onLeaveJunction(StopLine stopLine, long now) {
        if (requested.remove(stopLine)) {
            stopLine.getJunction().getSignal().releasePreemption(this, now);
        }
    }

    @Override
    protected void onRemoved(long now) {
        for (StopLine sl : requested) {
            sl.getJunction().getSignal().releasePreemption(this, now);
        }
        requested.clear();
    }

    @Override
    public Color beaconColor(long animMillis) {
        return (animMillis / 180) % 2 == 0 ? BEACON_RED : BEACON_BLUE;
    }

    /** Two roof beacons that swap colours. */
    protected void drawBeacons(Graphics2D g, long animMillis, double x) {
        Color a = beaconColor(animMillis);
        Color b = a == BEACON_RED ? BEACON_BLUE : BEACON_RED;
        g.setColor(a);
        g.fill(new Ellipse2D.Double(x, -width / 2 + 1, 4, 4));
        g.setColor(b);
        g.fill(new Ellipse2D.Double(x, width / 2 - 5, 4, 4));
    }

    @Override
    public String getStatusText() {
        return super.getStatusText() + " | " + getMission();
    }
}
