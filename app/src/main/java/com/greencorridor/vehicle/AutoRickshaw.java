package com.greencorridor.vehicle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/** The three-wheeled auto-rickshaw: yellow body, green canopy. */
public class AutoRickshaw extends Vehicle {

    private static final Color BODY = new Color(0xF4C20D);
    private static final Color CANOPY = new Color(0x2E7D32);

    public AutoRickshaw() {
        super(19, 12, 85, 65);
    }

    @Override
    public String getTypeCode() {
        return "AUTO";
    }

    @Override
    public String getDisplayName() {
        return "Auto-rickshaw";
    }

    @Override
    protected void drawBody(Graphics2D g, long animMillis) {
        fillBody(g, BODY, 8);
        g.setColor(CANOPY);
        g.fill(new RoundRectangle2D.Double(-length / 2 + 1, -width / 2 + 1, length - 7, width - 2, 6, 6));
    }
}
