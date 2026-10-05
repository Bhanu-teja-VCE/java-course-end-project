package com.greencorridor.vehicle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

public class Car extends Vehicle {

    private static final Color[] PALETTE = {
        new Color(0x1E88E5), new Color(0xCFD8DC), new Color(0x8E2430), new Color(0x00897B),
        new Color(0x37474F), new Color(0xF5F5F5), new Color(0xF9A825), new Color(0x5E35B1)
    };
    private static final Color GLASS = new Color(0x263238);

    private final Color color;

    public Car() {
        super(26, 13, 110, 75);
        this.color = PALETTE[number % PALETTE.length];
    }

    @Override
    public String getTypeCode() {
        return "CAR";
    }

    @Override
    public String getDisplayName() {
        return "Car";
    }

    @Override
    protected void drawBody(Graphics2D g, long animMillis) {
        fillBody(g, color, 7);
        g.setColor(GLASS);
        g.fill(new RoundRectangle2D.Double(length / 2 - 9, -width / 2 + 2, 4, width - 4, 2, 2));  // windscreen
        g.fill(new RoundRectangle2D.Double(-length / 2 + 3, -width / 2 + 2.5, 3, width - 5, 2, 2)); // rear window
    }
}
