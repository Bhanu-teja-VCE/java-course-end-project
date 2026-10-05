package com.greencorridor.vehicle;

import java.util.Map;
import java.util.Random;

/** Creates vehicles from their type codes. */
public final class VehicleFactory {

    public static final String AMBULANCE = "AMBULANCE";
    public static final String FIRE_ENGINE = "FIRE";

    private VehicleFactory() {
    }

    public static Vehicle create(String typeCode) {
        switch (typeCode) {
            case "CAR":
                return new Car();
            case "BIKE":
                return new Bike();
            case "AUTO":
                return new AutoRickshaw();
            case "BUS":
                return new Bus();
            case "TRUCK":
                return new Truck();
            case AMBULANCE:
                return new Ambulance();
            case FIRE_ENGINE:
                return new FireEngine();
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + typeCode);
        }
    }

    /** Picks a type at random, in proportion to the weights of the vehicle mix. */
    public static Vehicle randomFromMix(Map<String, Integer> mix, Random random) {
        int total = 0;
        for (int weight : mix.values()) {
            total += weight;
        }
        int r = random.nextInt(total);
        for (Map.Entry<String, Integer> e : mix.entrySet()) {
            r -= e.getValue();
            if (r < 0) {
                return create(e.getKey());
            }
        }
        throw new IllegalStateException("vehicle mix weights changed while picking");
    }
}
