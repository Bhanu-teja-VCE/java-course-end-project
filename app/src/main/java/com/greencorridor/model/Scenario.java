package com.greencorridor.model;

import com.greencorridor.exception.InvalidScenarioException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * A traffic situation to simulate: how many junctions, how busy the roads are, how the
 * signals behave and how emergency vehicles are handled.
 * Stored in the MySQL table "scenario" and can be exported to a file (it is Serializable).
 */
public class Scenario implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Vehicle type codes allowed in the vehicle mix. */
    public static final List<String> MIX_TYPES = Arrays.asList("CAR", "BIKE", "AUTO", "BUS", "TRUCK");

    public static final String DEFAULT_MIX = "CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7";

    private int id;                       // 0 = not saved in the database yet
    private String name = "";
    private String description = "";
    private int junctionCount = 3;
    private int mainRatePerMin = 18;      // vehicles per minute, per direction
    private int crossRatePerMin = 8;
    private SignalMode signalMode = SignalMode.FIXED;
    private PriorityMode priorityMode = PriorityMode.CORRIDOR;
    private int mainGreenSec = 12;
    private int crossGreenSec = 8;
    private int yellowSec = 3;
    private String vehicleMix = DEFAULT_MIX;
    private int emergencyEverySec = 0;    // 0 = only when dispatched by hand; every 3rd is a fire engine
    private int durationSec = 180;
    private int randomSeed = 42;

    public Scenario() {
    }

    public Scenario(String name) {
        this.name = name;
    }

    /**
     * Checks every field and throws on the first problem found.
     *
     * @throws InvalidScenarioException naming the field that is wrong
     */
    public void validate() throws InvalidScenarioException {
        if (name == null || name.trim().length() < 3) {
            throw new InvalidScenarioException("name", "Name must have at least 3 characters.");
        }
        if (name.trim().length() > 60) {
            throw new InvalidScenarioException("name", "Name can have at most 60 characters.");
        }
        if (description != null && description.length() > 255) {
            throw new InvalidScenarioException("description", "Description can have at most 255 characters.");
        }
        checkRange("junctionCount", "Number of junctions", junctionCount, 1, 3);
        checkRange("mainRatePerMin", "Main road traffic", mainRatePerMin, 0, 40);
        checkRange("crossRatePerMin", "Cross street traffic", crossRatePerMin, 0, 30);
        checkRange("mainGreenSec", "Main road green time", mainGreenSec, 5, 60);
        checkRange("crossGreenSec", "Cross street green time", crossGreenSec, 5, 60);
        checkRange("yellowSec", "Yellow time", yellowSec, 2, 6);
        checkRange("durationSec", "Duration", durationSec, 30, 1800);
        if (emergencyEverySec != 0) {
            checkRange("emergencyEverySec", "Emergency interval", emergencyEverySec, 10, 600);
        }
        if (signalMode == null) {
            throw new InvalidScenarioException("signalMode", "Choose a signal mode.");
        }
        if (priorityMode == null) {
            throw new InvalidScenarioException("priorityMode", "Choose a priority mode.");
        }
        parseVehicleMix();
    }

    private static void checkRange(String field, String label, int value, int min, int max)
            throws InvalidScenarioException {
        if (value < min || value > max) {
            throw new InvalidScenarioException(field,
                    label + " must be between " + min + " and " + max + " (it is " + value + ").");
        }
    }

    /**
     * Turns a text like "CAR:45,BIKE:25,BUS:10" into an ordered map {CAR=45, BIKE=25, BUS=10}.
     *
     * @throws InvalidScenarioException if the text is not in TYPE:weight form
     */
    public Map<String, Integer> parseVehicleMix() throws InvalidScenarioException {
        Map<String, Integer> mix = new LinkedHashMap<String, Integer>();
        if (vehicleMix == null || vehicleMix.trim().isEmpty()) {
            throw new InvalidScenarioException("vehicleMix", "Vehicle mix is empty. Example: " + DEFAULT_MIX);
        }
        StringTokenizer pairs = new StringTokenizer(vehicleMix, ",");
        int total = 0;
        while (pairs.hasMoreTokens()) {
            String pair = pairs.nextToken().trim();
            StringTokenizer parts = new StringTokenizer(pair, ":");
            if (parts.countTokens() != 2) {
                throw new InvalidScenarioException("vehicleMix", "\"" + pair + "\" should look like CAR:45");
            }
            String type = parts.nextToken().trim().toUpperCase();
            String weightText = parts.nextToken().trim();
            if (!MIX_TYPES.contains(type)) {
                throw new InvalidScenarioException("vehicleMix",
                        "Unknown vehicle type \"" + type + "\". Use " + MIX_TYPES);
            }
            if (mix.containsKey(type)) {
                throw new InvalidScenarioException("vehicleMix", type + " appears twice in the vehicle mix.");
            }
            int weight;
            try {
                weight = Integer.parseInt(weightText);
            } catch (NumberFormatException e) {
                throw new InvalidScenarioException("vehicleMix", "\"" + weightText + "\" is not a whole number.");
            }
            if (weight < 0 || weight > 100) {
                throw new InvalidScenarioException("vehicleMix", "Weight of " + type + " must be 0 to 100.");
            }
            mix.put(type, weight);
            total += weight;
        }
        if (total == 0) {
            throw new InvalidScenarioException("vehicleMix", "At least one vehicle type needs a weight above 0.");
        }
        return mix;
    }

    /** A field-by-field copy, used by "Duplicate" and by the editor form. */
    public Scenario copy() {
        Scenario s = new Scenario(name);
        s.id = id;
        s.description = description;
        s.junctionCount = junctionCount;
        s.mainRatePerMin = mainRatePerMin;
        s.crossRatePerMin = crossRatePerMin;
        s.signalMode = signalMode;
        s.priorityMode = priorityMode;
        s.mainGreenSec = mainGreenSec;
        s.crossGreenSec = crossGreenSec;
        s.yellowSec = yellowSec;
        s.vehicleMix = vehicleMix;
        s.emergencyEverySec = emergencyEverySec;
        s.durationSec = durationSec;
        s.randomSeed = randomSeed;
        return s;
    }

    /** Scenarios available when the app runs without a database. Same as the SQL seed data. */
    public static List<Scenario> builtInDefaults() {
        List<Scenario> list = new ArrayList<Scenario>();

        Scenario peak = new Scenario("Morning Peak - Green Corridor");
        peak.setDescription("Busy main road, 3 junctions, an emergency every 40 s. Junctions ahead are cleared in advance.");
        peak.setMainRatePerMin(22);
        peak.setCrossRatePerMin(10);
        peak.setEmergencyEverySec(40);
        peak.setDurationSec(240);
        peak.setPriorityMode(PriorityMode.CORRIDOR);
        list.add(peak);

        Scenario local = peak.copy();
        local.setName("Morning Peak - Local Sensor");
        local.setDescription("Same traffic. A junction turns green only when the emergency vehicle reaches its sensor.");
        local.setPriorityMode(PriorityMode.LOCAL);
        list.add(local);

        Scenario none = peak.copy();
        none.setName("Morning Peak - No Priority");
        none.setDescription("Same traffic. Emergency vehicles wait at red lights like every other vehicle.");
        none.setPriorityMode(PriorityMode.NONE);
        list.add(none);

        Scenario evening = new Scenario("Evening Rush - Adaptive Signals");
        evening.setDescription("Heavy cross traffic handled by vehicle-actuated signals, with a green corridor.");
        evening.setMainRatePerMin(18);
        evening.setCrossRatePerMin(14);
        evening.setSignalMode(SignalMode.ADAPTIVE);
        evening.setEmergencyEverySec(60);
        evening.setDurationSec(240);
        list.add(evening);

        Scenario hospital = new Scenario("Hospital Road - Frequent Emergencies");
        hospital.setDescription("Two junctions near the city hospital. An emergency every 25 s; every third one is a fire engine on a cross street.");
        hospital.setJunctionCount(2);
        hospital.setMainRatePerMin(20);
        hospital.setCrossRatePerMin(10);
        hospital.setEmergencyEverySec(25);
        hospital.setDurationSec(180);
        list.add(hospital);

        Scenario single = new Scenario("Single Junction Demo");
        single.setDescription("One junction and light traffic. Good for explaining the signal cycle in the viva.");
        single.setJunctionCount(1);
        single.setMainRatePerMin(10);
        single.setCrossRatePerMin(6);
        single.setDurationSec(120);
        list.add(single);

        Scenario night = new Scenario("Night - Light Traffic");
        night.setDescription("Few vehicles. Adaptive signals keep the main road green until someone arrives.");
        night.setMainRatePerMin(6);
        night.setCrossRatePerMin(3);
        night.setSignalMode(SignalMode.ADAPTIVE);
        night.setVehicleMix("CAR:40,BIKE:30,AUTO:10,BUS:5,TRUCK:15");
        night.setDurationSec(120);
        list.add(night);

        return list;
    }

    @Override
    public String toString() {
        return name;
    }

    // ----- getters and setters -----

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    public int getJunctionCount() {
        return junctionCount;
    }

    public void setJunctionCount(int junctionCount) {
        this.junctionCount = junctionCount;
    }

    public int getMainRatePerMin() {
        return mainRatePerMin;
    }

    public void setMainRatePerMin(int mainRatePerMin) {
        this.mainRatePerMin = mainRatePerMin;
    }

    public int getCrossRatePerMin() {
        return crossRatePerMin;
    }

    public void setCrossRatePerMin(int crossRatePerMin) {
        this.crossRatePerMin = crossRatePerMin;
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

    public int getMainGreenSec() {
        return mainGreenSec;
    }

    public void setMainGreenSec(int mainGreenSec) {
        this.mainGreenSec = mainGreenSec;
    }

    public int getCrossGreenSec() {
        return crossGreenSec;
    }

    public void setCrossGreenSec(int crossGreenSec) {
        this.crossGreenSec = crossGreenSec;
    }

    public int getYellowSec() {
        return yellowSec;
    }

    public void setYellowSec(int yellowSec) {
        this.yellowSec = yellowSec;
    }

    public String getVehicleMix() {
        return vehicleMix;
    }

    public void setVehicleMix(String vehicleMix) {
        this.vehicleMix = vehicleMix == null ? "" : vehicleMix.trim().toUpperCase();
    }

    public int getEmergencyEverySec() {
        return emergencyEverySec;
    }

    public void setEmergencyEverySec(int emergencyEverySec) {
        this.emergencyEverySec = emergencyEverySec;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public int getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(int randomSeed) {
        this.randomSeed = randomSeed;
    }
}
