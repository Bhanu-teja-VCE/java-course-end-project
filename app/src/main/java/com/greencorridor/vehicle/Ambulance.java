package com.greencorridor.vehicle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** An ambulance on its way to the City Hospital. Final: nothing extends it. */
public final class Ambulance extends EmergencyVehicle {

    private static final Color STRIPE = new Color(0xD32F2F);

    public Ambulance() {
        super(30, 14, 150, 110);
    }

    @Override
    public String getTypeCode() {
        return "AMBULANCE";
    }

    @Override
    public String getDisplayName() {
        return "Ambulance";
    }

    @Override
    public int getUrgency() {
        return URGENCY_MEDICAL;
    }

    @Override
    public String getMission() {
        return "Patient to City Hospital";
    }

    @Override
    protected void drawBody(Graphics2D g, long animMillis) {
        fillBody(g, Color.WHITE, 5);
        g.setColor(STRIPE);
        g.fill(new Rectangle2D.Double(-length / 2 + 1, -1, length - 2, 2));
        g.fill(new Rectangle2D.Double(-6, -4.5, 9, 3));      // red cross on the roof
        g.fill(new Rectangle2D.Double(-3, -7.5, 3, 9));
        drawBeacons(g, animMillis, length / 2 - 9);
    }
}
