package com.greencorridor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The results of one simulation run, shown in a dialog when the run ends. */
public class RunSummary {

    private String scenarioName;
    private SignalMode signalMode;
    private PriorityMode priorityMode;
    private String status;                 // COMPLETED or STOPPED
    private int runId = -1;                // -1 = not saved in MySQL
    private boolean verifiedByDatabase;
    private int simSeconds;
    private int tripsCompleted;
    private int vehiclesTurnedAway;
    private double avgDelaySec;
    private double p95DelaySec;
    private double maxDelaySec;
    private double avgStops;
    private double throughputPerMin;
    private int emergencyTrips;
    private double emergencyAvgDelaySec;
    private int preemptions;
    private double avgResponseSec;
    private Map<String, double[]> byType = new TreeMap<String, double[]>();
    private List<TripRecord> mostDelayed = new ArrayList<TripRecord>();

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public SignalMode getSignalMode() {
        return signalMode;
    }

    public void setSignalMode(SignalMode signalMode) {
        this.signalMode = signalMode;
    }

    public PriorityMode getPriorityMode() {
        return priorityMode;
    }

    public void setPriorityMode(PriorityMode priorityMode) {
        this.priorityMode = priorityMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRunId() {
        return runId;
    }

    public void setRunId(int runId) {
        this.runId = runId;
    }

    public boolean isVerifiedByDatabase() {
        return verifiedByDatabase;
    }

    public void setVerifiedByDatabase(boolean verifiedByDatabase) {
        this.verifiedByDatabase = verifiedByDatabase;
    }

    public int getSimSeconds() {
        return simSeconds;
    }

    public void setSimSeconds(int simSeconds) {
        this.simSeconds = simSeconds;
    }

    public int getTripsCompleted() {
        return tripsCompleted;
    }

    public void setTripsCompleted(int tripsCompleted) {
        this.tripsCompleted = tripsCompleted;
    }

    public int getVehiclesTurnedAway() {
        return vehiclesTurnedAway;
    }

    public void setVehiclesTurnedAway(int vehiclesTurnedAway) {
        this.vehiclesTurnedAway = vehiclesTurnedAway;
    }

    public double getAvgDelaySec() {
        return avgDelaySec;
    }

    public void setAvgDelaySec(double avgDelaySec) {
        this.avgDelaySec = avgDelaySec;
    }

    public double getP95DelaySec() {
        return p95DelaySec;
    }

    public void setP95DelaySec(double p95DelaySec) {
        this.p95DelaySec = p95DelaySec;
    }

    public double getMaxDelaySec() {
        return maxDelaySec;
    }

    public void setMaxDelaySec(double maxDelaySec) {
        this.maxDelaySec = maxDelaySec;
    }

    public double getAvgStops() {
        return avgStops;
    }

    public void setAvgStops(double avgStops) {
        this.avgStops = avgStops;
    }

    public double getThroughputPerMin() {
        return throughputPerMin;
    }

    public void setThroughputPerMin(double throughputPerMin) {
        this.throughputPerMin = throughputPerMin;
    }

    public int getEmergencyTrips() {
        return emergencyTrips;
    }

    public void setEmergencyTrips(int emergencyTrips) {
        this.emergencyTrips = emergencyTrips;
    }

    public double getEmergencyAvgDelaySec() {
        return emergencyAvgDelaySec;
    }

    public void setEmergencyAvgDelaySec(double emergencyAvgDelaySec) {
        this.emergencyAvgDelaySec = emergencyAvgDelaySec;
    }

    public int getPreemptions() {
        return preemptions;
    }

    public void setPreemptions(int preemptions) {
        this.preemptions = preemptions;
    }

    public double getAvgResponseSec() {
        return avgResponseSec;
    }

    public void setAvgResponseSec(double avgResponseSec) {
        this.avgResponseSec = avgResponseSec;
    }

    /** The most delayed ordinary trips, worst first. */
    public List<TripRecord> getMostDelayed() {
        return mostDelayed;
    }

    public void setMostDelayed(List<TripRecord> mostDelayed) {
        this.mostDelayed = mostDelayed;
    }

    /** Vehicle type -> {trips, average delay in seconds}, sorted by type name. */
    public Map<String, double[]> getByType() {
        return byType;
    }

    public void setByType(Map<String, double[]> byType) {
        this.byType = byType;
    }
}
