package com.greencorridor.vehicle;

/** Something that can claim right of way. A higher urgency is served first. */
public interface Prioritized {

    int URGENCY_FIRE = 2;
    int URGENCY_MEDICAL = 3;

    int getUrgency();
}
