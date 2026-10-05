package com.greencorridor.ui;

import com.greencorridor.engine.SignalController;
import com.greencorridor.engine.SignalLight;
import com.greencorridor.engine.SimConfig;
import com.greencorridor.engine.SimulationEngine;
import com.greencorridor.model.Axis;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.road.Junction;
import com.greencorridor.road.Lane;
import com.greencorridor.road.RoadNetwork;
import com.greencorridor.road.StopLine;
import com.greencorridor.vehicle.EmergencyResponder;
import com.greencorridor.vehicle.Vehicle;
import com.greencorridor.vehicle.VehicleFactory;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The live map. It paints the simulation about 30 times a second and turns mouse and
 * keyboard input into commands:
 * <ul>
 *   <li>hover a vehicle: tooltip with its thread state; click it to keep it selected</li>
 *   <li>click a junction: traffic-police override (end the current green)</li>
 *   <li>click a road entry arrow: send an ambulance (Shift+click: fire engine)</li>
 *   <li>keys: Enter start, Esc stop, Space pause, A ambulance, F fire engine,
 *       1-4 speed, H help</li>
 * </ul>
 * Mouse and key events are delivered through adapter classes (MouseAdapter, KeyAdapter)
 * that override only the methods we need: the delegation event model.
 */
public class SimulationCanvas extends JComponent {

    private static final long serialVersionUID = 1L;

    /** What the canvas asks its owner to do. */
    public interface Controls {
        void startRun();

        void stopRun();

        void togglePause();

        void dispatch(String typeCode, Lane lane);

        void setSpeedIndex(int index);
    }

    private final Controls controls;
    private volatile SimulationEngine engine;
    private RoadNetwork previewNetwork = new RoadNetwork(3);

    private BufferedImage background;
    private RoadNetwork backgroundFor;
    private int backgroundW;
    private int backgroundH;

    private double scale = 1;
    private double offsetX;
    private double offsetY;

    private Vehicle hovered;
    private Vehicle selected;
    private Junction hoveredJunction;
    private Lane hoveredEntry;
    private Point2D mouse;
    private boolean showHelp;

    public SimulationCanvas(Controls controls) {
        this.controls = controls;
        setPreferredSize(new Dimension(SimConfig.WORLD_WIDTH, SimConfig.WORLD_HEIGHT));
        setMinimumSize(new Dimension(600, 320));
        setFocusable(true);
        setToolTipText(null);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouse = null;
                hovered = null;
                hoveredJunction = null;
                hoveredEntry = null;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handleClick(e);
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e);
            }
        });
    }

    public void setEngine(SimulationEngine engine) {
        this.engine = engine;
        selected = null;
        hovered = null;
        repaint();
    }

    public SimulationEngine getEngine() {
        return engine;
    }

    /** Map shown while no simulation runs (follows the chosen scenario). */
    public void setPreview(int junctionCount) {
        if (previewNetwork.getJunctions().size() != junctionCount) {
            previewNetwork = new RoadNetwork(junctionCount);
            repaint();
        }
    }

    private RoadNetwork network() {
        SimulationEngine e = engine;
        return e != null ? e.getNetwork() : previewNetwork;
    }

    // ----- input -----

    private Point2D toWorld(int x, int y) {
        return new Point2D.Double((x - offsetX) / scale, (y - offsetY) / scale);
    }

    private void updateHover(int x, int y) {
        mouse = new Point2D.Double(x, y);
        Point2D w = toWorld(x, y);
        RoadNetwork net = network();
        hovered = null;
        SimulationEngine e = engine;
        if (e != null) {
            List<Vehicle> vehicles = e.getVehicles();
            for (int i = vehicles.size() - 1; i >= 0; i--) {
                if (vehicles.get(i).contains(w.getX(), w.getY())) {
                    hovered = vehicles.get(i);
                    break;
                }
            }
        }
        hoveredEntry = hovered == null ? net.entryLaneNear(w.getX(), w.getY(), 26) : null;
        hoveredJunction = hovered == null && hoveredEntry == null ? net.junctionAt(w.getX(), w.getY(), 4) : null;
        boolean clickable = e != null && e.isRunning() && (hoveredEntry != null || hoveredJunction != null);
        setCursor(Cursor.getPredefinedCursor(clickable || hovered != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    private void handleClick(MouseEvent e) {
        updateHover(e.getX(), e.getY());
        SimulationEngine eng = engine;
        if (hovered != null) {
            selected = hovered == selected ? null : hovered;
        } else if (eng != null && eng.isRunning() && hoveredEntry != null) {
            controls.dispatch(e.isShiftDown() ? VehicleFactory.FIRE_ENGINE : VehicleFactory.AMBULANCE, hoveredEntry);
        } else if (eng != null && eng.isRunning() && hoveredJunction != null) {
            eng.forceSwitch(hoveredJunction);
        } else {
            selected = null;
        }
        repaint();
    }

    private void handleKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_SPACE:
                controls.togglePause();
                break;
            case KeyEvent.VK_ENTER:
                controls.startRun();
                break;
            case KeyEvent.VK_ESCAPE:
                controls.stopRun();
                break;
            case KeyEvent.VK_A:
                controls.dispatch(VehicleFactory.AMBULANCE, null);
                break;
            case KeyEvent.VK_F:
                controls.dispatch(VehicleFactory.FIRE_ENGINE, null);
                break;
            case KeyEvent.VK_1:
            case KeyEvent.VK_2:
            case KeyEvent.VK_3:
            case KeyEvent.VK_4:
                controls.setSpeedIndex(e.getKeyCode() - KeyEvent.VK_1);
                break;
            case KeyEvent.VK_H:
                showHelp = !showHelp;
                repaint();
                break;
            default:
                break;
        }
    }

    // ----- painting -----

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(new Color(0x2B3033));
        g.fillRect(0, 0, w, h);

        scale = Math.min(w / (double) SimConfig.WORLD_WIDTH, h / (double) SimConfig.WORLD_HEIGHT);
        offsetX = (w - SimConfig.WORLD_WIDTH * scale) / 2;
        offsetY = (h - SimConfig.WORLD_HEIGHT * scale) / 2;
        RoadNetwork net = network();
        ensureBackground(net);
        g.drawImage(background, (int) Math.round(offsetX), (int) Math.round(offsetY), null);

        AffineTransform screen = g.getTransform();
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.clip(new Rectangle2D.Double(0, 0, SimConfig.WORLD_WIDTH, SimConfig.WORLD_HEIGHT));
        long t = System.currentTimeMillis();
        SimulationEngine e = engine;

        if (e != null) {
            if (e.getPriorityMode() == PriorityMode.LOCAL) {
                paintSensors(g, net);
            }
            paintCorridors(g, e, t);
            paintVehicles(g, e, t);
        }
        paintSignals(g, net);
        if (e != null) {
            paintPriorityRings(g, net, t);
        }
        paintEntries(g, net, e, t);
        if (selected != null && selected.getThread() != null && selected.getThread().isAlive()) {
            paintHighlight(g, selected, new Color(0x00B0FF));
        }
        if (hovered != null && hovered != selected) {
            paintHighlight(g, hovered, Color.WHITE);
        }

        g.setTransform(screen);
        g.setClip(null);
        if (e != null) {
            paintHud(g, e);
        }
        Vehicle info = hovered != null ? hovered : selected;
        if (info != null && info.getThread() != null && info.getThread().isAlive()) {
            paintVehicleInfo(g, info, hovered != null && mouse != null ? mouse : vehicleScreenPoint(info));
        } else if (hoveredJunction != null && e != null && e.isRunning() && mouse != null) {
            paintCaption(g, mouse, hoveredJunction.getName() + ": click to end the current green (traffic police)");
        } else if (hoveredEntry != null && e != null && e.isRunning() && mouse != null) {
            paintCaption(g, mouse, "Click: send an ambulance here   Shift+click: fire engine");
        }
        if (e == null) {
            paintBanner(g, "Choose a scenario and press Start (or Enter)",
                    "Hover over vehicles, click junctions and road entries once it runs. Press H for help.");
        } else if (e.isPaused()) {
            paintBanner(g, "Paused", "Press Space or the Resume button to continue");
        }
        if (showHelp) {
            paintHelp(g);
        }
        g.dispose();
    }

    private void ensureBackground(RoadNetwork net) {
        int bw = (int) Math.max(1, Math.round(SimConfig.WORLD_WIDTH * scale));
        int bh = (int) Math.max(1, Math.round(SimConfig.WORLD_HEIGHT * scale));
        if (background == null || backgroundFor != net || bw != backgroundW || bh != backgroundH) {
            background = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_RGB);
            Graphics2D bg = background.createGraphics();
            bg.scale(bw / (double) SimConfig.WORLD_WIDTH, bh / (double) SimConfig.WORLD_HEIGHT);
            WorldPainter.paint(bg, net);
            bg.dispose();
            backgroundFor = net;
            backgroundW = bw;
            backgroundH = bh;
        }
    }

    private void paintSensors(Graphics2D g, RoadNetwork net) {
        g.setColor(Theme.SENSOR);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
        for (Lane lane : net.getLanes()) {
            for (StopLine sl : lane.getStopLines()) {
                double s = sl.getStopS() - SimConfig.LOCAL_DETECT_RANGE;
                double x = lane.xAt(s);
                double y = lane.yAt(s);
                AffineTransform old = g.getTransform();
                g.translate(x, y);
                g.rotate(lane.getDirection().angle());
                g.draw(new Rectangle2D.Double(-8, -11, 16, 22));
                g.setTransform(old);
            }
        }
    }

    /** Green band along the route ahead of every emergency vehicle with a green corridor. */
    private void paintCorridors(Graphics2D g, SimulationEngine e, long t) {
        if (e.getPriorityMode() != PriorityMode.CORRIDOR) {
            return;
        }
        List<Vehicle> emergencies = new ArrayList<Vehicle>(e.getApproachingVehicles());
        for (Vehicle v : e.getVehicles()) {
            if (v.isEmergency()) {
                emergencies.add(v);
            }
        }
        for (Vehicle v : emergencies) {
            Lane lane = v.getLane();
            boolean onMap = v.getThread() != null && v.getThread().getState() != Thread.State.NEW;
            double from = onMap ? v.getFrontS() : SimConfig.ENTRY_MARGIN - 20;
            double to = lane.getLength() - SimConfig.ENTRY_MARGIN;
            if (to <= from) {
                continue;
            }
            g.setColor(Theme.CORRIDOR);
            g.setStroke(new BasicStroke(26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(lane.xAt(from), lane.yAt(from), lane.xAt(to), lane.yAt(to)));
            // moving chevrons
            g.setColor(new Color(255, 255, 255, 140));
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double phase = (t / 12.0) % 40;
            for (double s = from + 20 + phase; s < to; s += 40) {
                AffineTransform old = g.getTransform();
                g.translate(lane.xAt(s), lane.yAt(s));
                g.rotate(lane.getDirection().angle());
                Path2D chevron = new Path2D.Double();
                chevron.moveTo(-4, -7);
                chevron.lineTo(3, 0);
                chevron.lineTo(-4, 7);
                g.draw(chevron);
                g.setTransform(old);
            }
        }
    }

    private void paintVehicles(Graphics2D g, SimulationEngine e, long t) {
        List<Vehicle> emergencies = new ArrayList<Vehicle>();
        for (Lane lane : e.getNetwork().getLanes()) {
            for (Vehicle v : lane.snapshot()) {
                if (v.isEmergency()) {
                    emergencies.add(v);
                } else {
                    v.draw(g, t);              // dynamic method dispatch: Car, Bus, Bike...
                }
            }
        }
        for (Vehicle v : emergencies) {         // emergency vehicles on top, with a siren glow
            if (v instanceof EmergencyResponder) {
                Color c = ((EmergencyResponder) v).beaconColor(t);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
                g.fill(new Ellipse2D.Double(v.getCenterX() - 26, v.getCenterY() - 26, 52, 52));
            }
            v.draw(g, t);
        }
    }

    private void paintSignals(Graphics2D g, RoadNetwork net) {
        for (Lane lane : net.getLanes()) {
            for (StopLine sl : lane.getStopLines()) {
                SignalController signal = sl.getJunction().getSignal();
                SignalLight light = signal == null ? null : signal.getLight(sl.getAxis());
                double s = sl.getStopS() - 3;
                double off = SimConfig.LANE_OFFSET + 20;
                double x = lane.xAt(s) + lane.getDirection().getLeftX() * off;
                double y = lane.yAt(s) + lane.getDirection().getLeftY() * off;
                AffineTransform old = g.getTransform();
                g.translate(x, y);
                g.rotate(lane.getDirection().angle() + Math.PI / 2);
                g.setColor(Theme.HOUSING);
                g.fill(new RoundRectangle2D.Double(-13, -5, 26, 10, 6, 6));
                lamp(g, -8, Theme.LIGHT_RED, light == SignalLight.RED);
                lamp(g, 0, Theme.LIGHT_YELLOW, light == SignalLight.YELLOW);
                lamp(g, 8, Theme.LIGHT_GREEN, light == SignalLight.GREEN);
                g.setTransform(old);
            }
        }
    }

    private static void lamp(Graphics2D g, double x, Color color, boolean on) {
        if (on) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
            g.fill(new Ellipse2D.Double(x - 6, -6, 12, 12));
            g.setColor(color);
        } else {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
        }
        g.fill(new Ellipse2D.Double(x - 3.2, -3.2, 6.4, 6.4));
    }

    private void paintPriorityRings(Graphics2D g, RoadNetwork net, long t) {
        for (Junction j : net.getJunctions()) {
            SignalController signal = j.getSignal();
            if (signal == null || signal.getPriorityAxis() == null) {
                continue;
            }
            float pulse = (float) (0.5 + 0.5 * Math.sin(t / 160.0));
            double r = SimConfig.HALF_ROAD + 6 + 4 * pulse;
            g.setColor(new Color(52, 199, 89, (int) (120 + 100 * pulse)));
            g.setStroke(new BasicStroke(3f));
            g.draw(new RoundRectangle2D.Double(j.getX() - r, j.getY() - r, 2 * r, 2 * r, 14, 14));
            String holder = signal.getPriorityHolder();
            String text = "PRIORITY" + (holder == null ? "" : ": " + holder);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            double bx = j.getX() - fm.stringWidth(text) / 2.0 - 6;
            double by = j.getY() + SimConfig.HALF_ROAD + 14;
            g.setColor(new Color(0x1B7F4B));
            g.fill(new RoundRectangle2D.Double(bx, by, fm.stringWidth(text) + 12, 17, 8, 8));
            g.setColor(Color.WHITE);
            g.drawString(text, (float) bx + 6, (float) by + 12.5f);
        }
    }

    /** Arrows where traffic enters, with the number of vehicles queued off the map. */
    private void paintEntries(Graphics2D g, RoadNetwork net, SimulationEngine e, long t) {
        List<Vehicle> approaching = e == null ? new ArrayList<Vehicle>() : e.getApproachingVehicles();
        for (Lane lane : net.getLanes()) {
            double s = SimConfig.ENTRY_MARGIN + 14;
            double x = lane.xAt(s);
            double y = lane.yAt(s);
            boolean hot = lane == hoveredEntry && e != null && e.isRunning();
            Vehicle incoming = null;
            for (Vehicle v : approaching) {
                if (v.getLane() == lane) {
                    incoming = v;
                }
            }
            if (incoming instanceof EmergencyResponder) {
                Color c = ((EmergencyResponder) incoming).beaconColor(t);
                double pr = 15 + 4 * Math.sin(t / 120.0);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
                g.fill(new Ellipse2D.Double(x - pr, y - pr, 2 * pr, 2 * pr));
            }
            g.setColor(hot ? new Color(0xD32F2F) : new Color(0, 0, 0, 120));
            g.fill(new Ellipse2D.Double(x - 11, y - 11, 22, 22));
            AffineTransform old = g.getTransform();
            g.translate(x, y);
            g.rotate(lane.getDirection().angle());
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(-5, -5);
            arrow.lineTo(2, 0);
            arrow.lineTo(-5, 5);
            g.draw(arrow);
            g.setTransform(old);
            int queued = lane.entryQueueSize();
            if (queued > 0) {
                String text = "+" + queued;
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
                FontMetrics fm = g.getFontMetrics();
                double bx = x + 9;
                double by = y - 20;
                g.setColor(queued >= SimConfig.ENTRY_QUEUE_CAP ? Theme.DANGER : new Color(0x37474F));
                g.fill(new RoundRectangle2D.Double(bx, by, fm.stringWidth(text) + 8, 14, 7, 7));
                g.setColor(Color.WHITE);
                g.drawString(text, (float) bx + 4, (float) by + 10.5f);
            }
        }
    }

    private void paintHighlight(Graphics2D g, Vehicle v, Color color) {
        double r = Math.max(v.getLength(), 16) / 2 + 6;
        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(v.getCenterX() - r, v.getCenterY() - r, 2 * r, 2 * r));
    }

    private Point2D vehicleScreenPoint(Vehicle v) {
        return new Point2D.Double(offsetX + v.getCenterX() * scale, offsetY + v.getCenterY() * scale);
    }

    // ----- overlays in screen coordinates -----

    private void paintHud(Graphics2D g, SimulationEngine e) {
        long now = e.getClock().now() / 1000;
        int duration = e.getScenario().getDurationSec();
        String text = String.format("%02d:%02d / %02d:%02d   x%d   %s   %s", now / 60, now % 60, duration / 60,
                duration % 60, e.getClock().getSpeed(), e.getSignalMode().getLabel(), e.getPriorityMode().getLabel());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int x = (int) (offsetX + SimConfig.WORLD_WIDTH * scale) - fm.stringWidth(text) - 30;
        int y = (int) (offsetY + SimConfig.WORLD_HEIGHT * scale) - 34;
        g.setColor(new Color(0, 0, 0, 160));
        g.fill(new RoundRectangle2D.Double(x, y, fm.stringWidth(text) + 20, 24, 12, 12));
        g.setColor(Color.WHITE);
        g.drawString(text, x + 10, y + 16);
        // progress bar
        double progress = Math.min(1, now / (double) duration);
        g.setColor(new Color(255, 255, 255, 60));
        g.fill(new Rectangle2D.Double(x + 10, y + 20, fm.stringWidth(text), 2));
        g.setColor(Theme.LIGHT_GREEN);
        g.fill(new Rectangle2D.Double(x + 10, y + 20, fm.stringWidth(text) * progress, 2));
    }

    private void paintVehicleInfo(Graphics2D g, Vehicle v, Point2D at) {
        Thread thread = v.getThread();
        List<String> lines = new ArrayList<String>();
        lines.add(v.getLabel() + "   (thread \"" + thread.getName() + "\")");
        lines.add("State " + thread.getState() + ",  priority " + thread.getPriority());
        lines.add(String.format("%.0f km/h,  waited %.1f s,  stops %d", v.getSpeedKmh(), v.getWaitSec(), v.getStops()));
        lines.add(v.getStatusText());
        paintBox(g, at, lines, v.isEmergency() ? new Color(0xB71C1C) : new Color(0x263238));
    }

    private void paintCaption(Graphics2D g, Point2D at, String text) {
        List<String> lines = new ArrayList<String>();
        lines.add(text);
        paintBox(g, at, lines, new Color(0x263238));
    }

    private void paintBox(Graphics2D g, Point2D at, List<String> lines, Color bg) {
        g.setFont(Theme.SMALL);
        FontMetrics fm = g.getFontMetrics();
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, fm.stringWidth(line));
        }
        int boxW = width + 16;
        int boxH = lines.size() * (fm.getHeight() + 1) + 10;
        int x = (int) at.getX() + 16;
        int y = (int) at.getY() + 16;
        if (x + boxW > getWidth() - 4) {
            x = (int) at.getX() - boxW - 12;
        }
        if (y + boxH > getHeight() - 4) {
            y = (int) at.getY() - boxH - 12;
        }
        g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 235));
        g.fill(new RoundRectangle2D.Double(x, y, boxW, boxH, 10, 10));
        g.setColor(Color.WHITE);
        int ty = y + 6 + fm.getAscent();
        for (int i = 0; i < lines.size(); i++) {
            g.setFont(i == 0 ? Theme.SMALL.deriveFont(Font.BOLD) : Theme.SMALL);
            g.drawString(lines.get(i), x + 8, ty);
            ty += fm.getHeight() + 1;
        }
    }

    private void paintBanner(Graphics2D g, String title, String subtitle) {
        g.setFont(Theme.TITLE.deriveFont(20f));
        FontMetrics tf = g.getFontMetrics();
        int w = Math.max(tf.stringWidth(title), g.getFontMetrics(Theme.BODY).stringWidth(subtitle)) + 40;
        int x = (getWidth() - w) / 2;
        int y = getHeight() / 2 - 40;
        g.setColor(new Color(0, 0, 0, 170));
        g.fill(new RoundRectangle2D.Double(x, y, w, 70, 16, 16));
        g.setColor(Color.WHITE);
        g.drawString(title, x + (w - tf.stringWidth(title)) / 2, y + 30);
        g.setFont(Theme.BODY);
        FontMetrics bf = g.getFontMetrics();
        g.setColor(new Color(0xE0E0E0));
        g.drawString(subtitle, x + (w - bf.stringWidth(subtitle)) / 2, y + 52);
    }

    private void paintHelp(Graphics2D g) {
        String[] lines = {
            "Keyboard",
            "  Enter  start the selected scenario        Esc  stop the run",
            "  Space  pause / resume                     1 2 3 4  speed x1 x2 x4 x8",
            "  A      ambulance to City Hospital         F  fire engine on a cross street",
            "  H      show / hide this help",
            "Mouse",
            "  Hover a vehicle to see its thread; click it to keep it selected",
            "  Click a junction to end its green early (traffic police override)",
            "  Click a road entry arrow to send an ambulance there (Shift: fire engine)"
        };
        g.setFont(Theme.MONO);
        FontMetrics fm = g.getFontMetrics();
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, fm.stringWidth(l));
        }
        w += 32;
        int h = lines.length * (fm.getHeight() + 2) + 24;
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        g.setColor(new Color(0, 0, 0, 200));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 16, 16));
        int ty = y + 16 + fm.getAscent();
        for (String l : lines) {
            g.setColor(l.startsWith(" ") ? Color.WHITE : Theme.LIGHT_GREEN);
            g.drawString(l, x + 16, ty);
            ty += fm.getHeight() + 2;
        }
    }

    /** Count of vehicle threads currently blocked in wait() at a red light. */
    public static int countWaitingAtRed(SimulationEngine e) {
        int n = 0;
        for (Vehicle v : e.getVehicles()) {
            if (v.isWaitingAtSignal()) {
                n++;
            }
        }
        return n;
    }

    /** Queue length on one axis of a junction, for the side panel. */
    public static int queued(Junction j, Axis axis) {
        return j.queued(axis, 400);
    }
}
