package com.greencorridor.vehicle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/** A two-wheeler: small, quick to accelerate. */
public class Bike extends Vehicle {

    private static final Color[] HELMETS = {
        new Color(0xE53935), new Color(0xFDD835), new Color(0x43A047), new Color(0x1E88E5), new Color(0xFFFFFF)
    };

    public Bike() {
        super(14, 6, 115, 95);
    }

    @Override
    public String getTypeCode() {
        return "BIKE";
    }

    @Override
    public String getDisplayName() {
        return "Bike";
    }

    @Override
    protected void drawBody(Graphics2D g, long animMillis) {
        fillBody(g, new Color(0x212121), 4);
        g.setColor(HELMETS[number % HELMETS.length]);
        g.fill(new Ellipse2D.Double(-3.5, -3.5, 7, 7));
    }
}
