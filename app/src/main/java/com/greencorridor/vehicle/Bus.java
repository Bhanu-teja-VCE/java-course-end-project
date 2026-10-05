package com.greencorridor.vehicle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** A city bus: long and slow to pick up speed. */
public class Bus extends Vehicle {

    private static final Color BODY = new Color(0xEF6C00);
    private static final Color ROOF = new Color(0xFFE0B2);

    public Bus() {
        super(46, 15, 85, 40);
    }

    @Override
    public String getTypeCode() {
        return "BUS";
    }

    @Override
    public String getDisplayName() {
        return "Bus";
    }

    @Override
    protected void drawBody(Graphics2D g, long animMillis) {
        fillBody(g, BODY, 5);
        g.setColor(ROOF);
        g.fill(new Rectangle2D.Double(-length / 2 + 4, -2, length - 8, 4));
        g.setColor(new Color(0x263238));
        g.fill(new Rectangle2D.Double(length / 2 - 3, -width / 2 + 2, 2, width - 4));
    }
}
