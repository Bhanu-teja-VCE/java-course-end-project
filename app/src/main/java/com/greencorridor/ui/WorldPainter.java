package com.greencorridor.ui;

import com.greencorridor.engine.SimConfig;
import com.greencorridor.model.Direction;
import com.greencorridor.road.Junction;
import com.greencorridor.road.Lane;
import com.greencorridor.road.RoadNetwork;
import com.greencorridor.road.StopLine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Paints the parts of the map that never change: ground, city blocks, roads, lane
 * markings, zebra crossings and junction boxes. The canvas paints this once into an
 * image and reuses it every frame.
 */
public final class WorldPainter {

    private static final String[] TOP_PLACES = {"Bus Depot", "Central Market", "Tech Park"};
    private static final String[] BOTTOM_PLACES = {"City Park", "Govt. School", "Apartments"};

    private WorldPainter() {
    }

    public static void paint(Graphics2D g, RoadNetwork network) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = SimConfig.WORLD_WIDTH;
        int h = SimConfig.WORLD_HEIGHT;
        g.setColor(Theme.GROUND);
        g.fillRect(0, 0, w, h);

        paintBlocks(g, network);
        paintRoads(g, network);
    }

    // ----- city blocks -----

    private static void paintBlocks(Graphics2D g, RoadNetwork network) {
        List<Double> edges = new ArrayList<Double>();      // x borders between streets
        edges.add(0.0);
        for (Junction j : network.getJunctions()) {
            edges.add(j.getX() - SimConfig.HALF_ROAD - 6);
            edges.add(j.getX() + SimConfig.HALF_ROAD + 6);
        }
        edges.add((double) SimConfig.WORLD_WIDTH);
        double topBottom = SimConfig.ROAD_Y - SimConfig.HALF_ROAD - 6;
        double bottomTop = SimConfig.ROAD_Y + SimConfig.HALF_ROAD + 6;
        int regions = edges.size() / 2;
        for (int i = 0; i < regions; i++) {
            double x1 = edges.get(2 * i);
            double x2 = edges.get(2 * i + 1);
            boolean last = i == regions - 1;
            String top = last ? "CITY HOSPITAL" : TOP_PLACES[i % TOP_PLACES.length];
            String bottom = i == 0 ? "FIRE STATION" : BOTTOM_PLACES[(i - 1) % BOTTOM_PLACES.length];
            paintBlock(g, x1, 0, x2, topBottom, top, last, false);
            paintBlock(g, x1, bottomTop, x2, SimConfig.WORLD_HEIGHT, bottom, false, i == 0);
        }
    }

    private static void paintBlock(Graphics2D g, double x1, double y1, double x2, double y2, String name,
                                   boolean hospital, boolean fireStation) {
        double inset = 18;
        Shape block = new RoundRectangle2D.Double(x1 + inset, y1 + inset, x2 - x1 - 2 * inset, y2 - y1 - 2 * inset, 18, 18);
        boolean park = name.contains("Park");
        g.setColor(hospital ? Theme.HOSPITAL : park ? Theme.PARK : Theme.BLOCK);
        g.fill(block);
        g.setColor(Theme.BLOCK_EDGE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(block);
        double cx = (x1 + x2) / 2;
        double cy = (y1 + y2) / 2;
        if (park) {
            g.setColor(Theme.TREE);
            for (double tx = x1 + inset + 22; tx < x2 - inset - 14; tx += 46) {
                for (double ty = y1 + inset + 22; ty < y2 - inset - 14; ty += 44) {
                    if (Math.abs(ty - cy) > 26) {                // keep the middle free for the name
                        g.fill(new Ellipse2D.Double(tx - 9, ty - 9, 18, 18));
                    }
                }
            }
        }
        if (hospital || fireStation) {
            Color c = hospital ? new Color(0xD32F2F) : new Color(0xC62828);
            g.setColor(c);
            if (hospital) {
                g.fill(new Rectangle2D.Double(cx - 5, cy - 34, 10, 30));
                g.fill(new Rectangle2D.Double(cx - 15, cy - 24, 30, 10));
            } else {
                g.fill(new RoundRectangle2D.Double(cx - 16, cy - 34, 32, 26, 6, 6));
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                drawCentered(g, "101", cx, cy - 16);
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, hospital || fireStation ? Font.BOLD : Font.PLAIN, 13));
        g.setColor(hospital || fireStation ? new Color(0x8E1B1B) : new Color(0x6E6656));
        drawCentered(g, name, cx, cy + (hospital || fireStation ? 14 : 5));
    }

    // ----- roads -----

    private static void paintRoads(Graphics2D g, RoadNetwork network) {
        int w = SimConfig.WORLD_WIDTH;
        int h = SimConfig.WORLD_HEIGHT;
        int ry = SimConfig.ROAD_Y;
        int half = SimConfig.HALF_ROAD;

        // sidewalks first, then asphalt on top
        g.setColor(Theme.SIDEWALK);
        g.fillRect(0, ry - half - 6, w, 2 * half + 12);
        for (Junction j : network.getJunctions()) {
            g.fill(new Rectangle2D.Double(j.getX() - half - 6, 0, 2 * half + 12, h));
        }
        g.setColor(Theme.ASPHALT);
        g.fillRect(0, ry - half, w, 2 * half);
        for (Junction j : network.getJunctions()) {
            g.fill(new Rectangle2D.Double(j.getX() - half, 0, 2 * half, h));
        }

        // dashed centre lines, interrupted at the junctions
        g.setColor(Theme.MARKING);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{14f, 12f}, 0f));
        double from = 0;
        for (Junction j : network.getJunctions()) {
            g.draw(new Line2D.Double(from, ry, j.getX() - half - 18, ry));
            from = j.getX() + half + 18;
            g.draw(new Line2D.Double(j.getX(), 0, j.getX(), ry - half - 18));
            g.draw(new Line2D.Double(j.getX(), ry + half + 18, j.getX(), h));
        }
        g.draw(new Line2D.Double(from, ry, w, ry));

        for (Junction j : network.getJunctions()) {
            paintJunction(g, j);
        }
        for (Lane lane : network.getLanes()) {
            for (StopLine sl : lane.getStopLines()) {
                paintStopLine(g, lane, sl);
            }
        }
    }

    private static void paintJunction(Graphics2D g, Junction j) {
        int half = SimConfig.HALF_ROAD;
        double x = j.getX();
        double y = j.getY();
        // yellow "box junction" hatching: do not stop inside
        Shape box = new Rectangle2D.Double(x - half, y - half, 2 * half, 2 * half);
        Shape oldClip = g.getClip();
        g.clip(box);
        g.setColor(Theme.BOX_HATCH);
        g.setStroke(new BasicStroke(2f));
        for (int d = -2 * half; d <= 2 * half; d += 10) {
            g.draw(new Line2D.Double(x - half + d, y - half, x + half + d, y + half));
            g.draw(new Line2D.Double(x + half - d, y - half, x - half - d, y + half));
        }
        g.setClip(oldClip);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(box);

        // zebra crossings on the four mouths of the junction
        g.setColor(Theme.MARKING);
        for (int side = -1; side <= 1; side += 2) {
            double zx = x + side * (half + 8);
            for (double sy = y - half + 3; sy < y + half - 2; sy += 7) {
                g.fill(new Rectangle2D.Double(zx - 5, sy, 10, 3.5));
            }
            double zy = y + side * (half + 8);
            for (double sx = x - half + 3; sx < x + half - 2; sx += 7) {
                g.fill(new Rectangle2D.Double(sx, zy - 5, 3.5, 10));
            }
        }

        // name badge
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        String name = j.getName();
        FontMetrics fm = g.getFontMetrics();
        double bx = x + half + 10;
        double by = y - half - 58;
        g.setColor(Theme.LABEL_BG);
        g.fill(new RoundRectangle2D.Double(bx, by, fm.stringWidth(name) + 12, 18, 9, 9));
        g.setColor(Color.WHITE);
        g.drawString(name, (float) bx + 6, (float) by + 13);
    }

    private static void paintStopLine(Graphics2D g, Lane lane, StopLine sl) {
        Direction d = lane.getDirection();
        double x = lane.xAt(sl.getStopS());
        double y = lane.yAt(sl.getStopS());
        // across the approach half of the road only
        double lx = d.getLeftX();
        double ly = d.getLeftY();
        double off = SimConfig.LANE_OFFSET;
        g.setColor(Theme.MARKING);
        g.setStroke(new BasicStroke(3f));
        g.draw(new Line2D.Double(x - lx * off, y - ly * off, x + lx * off, y + ly * off));
    }

    static void drawCentered(Graphics2D g, String text, double cx, double baselineY) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) baselineY);
    }
}
