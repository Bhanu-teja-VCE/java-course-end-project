package com.greencorridor.vehicle;

import java.awt.Graphics2D;

/** Something that can paint itself on the simulation canvas. */
public interface Drawable {

    /**
     * @param g          graphics in world coordinates
     * @param animMillis wall-clock time, used for blinking lights
     */
    void draw(Graphics2D g, long animMillis);
}
