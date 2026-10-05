package com.greencorridor.engine;

import com.greencorridor.model.PreemptionEvent;
import com.greencorridor.model.TripRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Live statistics of a run. Many vehicle threads report here at the same time, so every
 * method is synchronized. The user interface asks for a {@link Snapshot} a few times a
 * second instead of calling many getters, which keeps lock contention low.
 */
public class StatsCollector {

    private final List<TripRecord> trips = new ArrayList<TripRecord>();
    private final List<PreemptionEvent> preemptions = new ArrayList<PreemptionEvent>();
    private int vehiclesCreated;
    private int vehiclesTurnedAway;
    private TripRecord lastEmergencyTrip;

    /** The five most delayed ordinary trips, kept sorted by a TreeSet (largest delay first). */
    private final TreeSet<TripRecord> mostDelayed = new TreeSet<TripRecord>(new Comparator<TripRecord>() {
        @Override
        public int compare(TripRecord a, TripRecord b) {
            int byDelay = Double.compare(b.getDelaySec(), a.getDelaySec());
            return byDelay != 0 ? byDelay : Integer.compare(a.getVehicleNo(), b.getVehicleNo());
        }
    });

    public synchronized void vehicleCreated() {
        vehiclesCreated++;
    }

    public synchronized void vehicleTurnedAway() {
        vehiclesTurnedAway++;
    }

    public synchronized void recordTrip(TripRecord trip) {
        trips.add(trip);
        if (trip.isEmergency()) {
            lastEmergencyTrip = trip;
        } else {
            mostDelayed.add(trip);
            if (mostDelayed.size() > 5) {
                mostDelayed.pollLast();         // drop the least delayed of the six
            }
        }
    }

    public synchronized void recordPreemption(PreemptionEvent event) {
        preemptions.add(event);
    }

    /** Computes all figures in one go. */
    public synchronized Snapshot snapshot(long simMillis) {
        Snapshot s = new Snapshot();
        s.vehiclesCreated = vehiclesCreated;
        s.vehiclesTurnedAway = vehiclesTurnedAway;
        s.tripsCompleted = trips.size();
        s.lastEmergencyTrip = lastEmergencyTrip;
        s.preemptions = preemptions.size();
        s.mostDelayed = new ArrayList<TripRecord>(mostDelayed);

        double[] delays = new double[trips.size()];
        int general = 0;
        double delaySum = 0;
        double stopSum = 0;
        double emergencyDelaySum = 0;
        Map<String, double[]> byType = new TreeMap<String, double[]>();   // type -> {count, delay sum}
        for (TripRecord t : trips) {
            double[] cell = byType.get(t.getVehicleType());
            if (cell == null) {
                cell = new double[2];
                byType.put(t.getVehicleType(), cell);
            }
            cell[0]++;
            cell[1] += t.getDelaySec();
            if (t.isEmergency()) {
                s.emergencyTrips++;
                emergencyDelaySum += t.getDelaySec();
            } else {
                delays[general++] = t.getDelaySec();
                delaySum += t.getDelaySec();
                stopSum += t.getStops();
            }
        }
        for (double[] cell : byType.values()) {
            cell[1] = cell[1] / cell[0];       // turn the sum into an average
        }
        s.byType = byType;

        if (general > 0) {
            double[] sorted = Arrays.copyOf(delays, general);
            Arrays.sort(sorted);
            s.avgDelaySec = delaySum / general;
            s.avgStops = stopSum / general;
            s.maxDelaySec = sorted[general - 1];
            s.p95DelaySec = sorted[Math.min(general - 1, (int) Math.ceil(general * 0.95) - 1)];
        }
        if (s.emergencyTrips > 0) {
            s.emergencyAvgDelaySec = emergencyDelaySum / s.emergencyTrips;
        }
        if (!preemptions.isEmpty()) {
            double sum = 0;
            for (PreemptionEvent p : preemptions) {
                sum += p.getResponseSec();
            }
            s.avgResponseSec = sum / preemptions.size();
        }
        double minutes = simMillis / 60000.0;
        s.throughputPerMin = minutes > 0 ? trips.size() / minutes : 0;
        return s;
    }

    /** Figures of a run at one moment. Plain fields, read only by the caller. */
    public static class Snapshot {
        public int vehiclesCreated;
        public int vehiclesTurnedAway;
        public int tripsCompleted;
        public double avgDelaySec;
        public double p95DelaySec;
        public double maxDelaySec;
        public double avgStops;
        public double throughputPerMin;
        public int emergencyTrips;
        public double emergencyAvgDelaySec;
        public TripRecord lastEmergencyTrip;
        public int preemptions;
        public double avgResponseSec;
        public Map<String, double[]> byType = new TreeMap<String, double[]>();
        public List<TripRecord> mostDelayed = new ArrayList<TripRecord>();
    }
}
