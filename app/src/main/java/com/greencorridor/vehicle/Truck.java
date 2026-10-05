package com.greencorridor.vehicle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** A goods truck: blue cab in front, brown cargo box behind. */
public class Truck extends Vehicle {

    private static final Color CAB = new Color(0x1565C0);
    private static final Color CARGO = new Color(0x8D6E63);

    public Truck() {
        super(40, 15, 75, 35);
    }

    @Override
    public String getTypeCode() {
        return "TRUCK";
    }

    @Override
    public String getDisplayName() {
        return "Truck";
    }

    @Override
    protected void drawBody(Graphics2D g, long animMillis) {
        fillBody(g, CARGO, 3);
        g.setColor(CAB);
        g.fill(new RoundRectangle2D.Double(length / 2 - 11, -width / 2, 11, width, 5, 5));
        g.setColor(CARGO.darker());
        for (int i = 0; i < 4; i++) {
            g.fill(new Rectangle2D.Double(-length / 2 + 4 + i * 6.5, -width / 2 + 2, 1, width - 4));
        }
    }
}
