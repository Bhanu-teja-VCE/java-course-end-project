package com.greencorridor.vehicle;

import java.awt.Color;

/**
 * An emergency vehicle: it can claim right of way (Prioritized) and draw its flashing
 * beacons (Drawable). An interface may extend several interfaces at once.
 */
public interface EmergencyResponder extends Prioritized, Drawable {

    /** What the vehicle is rushing to, shown in the event log. */
    String getMission();

    /** Colour of the beacon at a given moment, so it flashes. */
    Color beaconColor(long animMillis);
}
