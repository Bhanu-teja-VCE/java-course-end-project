package com.greencorridor.vehicle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** A fire engine answering a call. Final: nothing extends it. */
public final class FireEngine extends EmergencyVehicle {

    private static final Color BODY = new Color(0xC62828);
    private static final Color LADDER = new Color(0xB0BEC5);

    public FireEngine() {
        super(40, 15, 135, 85);
    }

    @Override
    public String getTypeCode() {
        return "FIRE";
    }

    @Override
    public String getDisplayName() {
        return "Fire engine";
    }

    @Override
    public int getUrgency() {
        return URGENCY_FIRE;
    }

    @Override
    public String getMission() {
        return "Fire call";
    }

    @Override
    protected void drawBody(Graphics2D g, long animMillis) {
        fillBody(g, BODY, 4);
        g.setColor(LADDER);
        g.fill(new Rectangle2D.Double(-length / 2 + 3, -4, length - 14, 1.5));
        g.fill(new Rectangle2D.Double(-length / 2 + 3, 2.5, length - 14, 1.5));
        for (int i = 0; i < 6; i++) {
            g.fill(new Rectangle2D.Double(-length / 2 + 4 + i * 4.5, -4, 1, 8));
        }
        drawBeacons(g, animMillis, length / 2 - 8);
    }
}
