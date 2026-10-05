package com.greencorridor.ui;

import com.greencorridor.engine.EngineListener;
import com.greencorridor.engine.SignalController;
import com.greencorridor.engine.SignalLight;
import com.greencorridor.engine.SimulationEngine;
import com.greencorridor.engine.StatsCollector;
import com.greencorridor.exception.IllegalSimulationStateException;
import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.model.Axis;
import com.greencorridor.model.Direction;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.RunSummary;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;
import com.greencorridor.road.Junction;
import com.greencorridor.road.Lane;
import com.greencorridor.road.RoadNetwork;
import com.greencorridor.vehicle.VehicleFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Live Simulation" tab: toolbar, map, live statistics and event log.
 * It owns the current {@link SimulationEngine} and implements {@link EngineListener}
 * to hear from it. Engine callbacks come from simulation threads, so they are passed
 * to the Swing event dispatch thread with SwingUtilities.invokeLater().
 */
public class LiveSimulationPanel extends JPanel implements EngineListener, SimulationCanvas.Controls {

    private static final long serialVersionUID = 1L;
    private static final int[] SPEEDS = {1, 2, 4, 8};
    private static final int MAX_LOG_LINES = 400;

    private final AppContext ctx;
    private final MainFrame frame;

    private final JComboBox<Scenario> scenarioBox = new JComboBox<Scenario>();
    private final JComboBox<SignalMode> signalBox = new JComboBox<SignalMode>(SignalMode.values());
    private final JComboBox<PriorityMode> priorityBox = new JComboBox<PriorityMode>(PriorityMode.values());
    private final JComboBox<String> speedBox = new JComboBox<String>(new String[]{"x1", "x2", "x4", "x8"});
    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton stopButton = new JButton("Stop");
    private final JButton ambulanceButton = new JButton("Send ambulance");
    private final JButton fireButton = new JButton("Send fire engine");
    private final JLabel modeHint = new JLabel();

    private final SimulationCanvas canvas;
    private final JTextArea logArea = new JTextArea(6, 40);

    private final StatTile timeTile = new StatTile("Simulated time", "Time inside the simulation (runs faster at x2, x4, x8)");
    private final StatTile onRoadTile = new StatTile("On the road", "Vehicles on the map now (one thread each)");
    private final StatTile tripsTile = new StatTile("Trips completed", "Vehicles that crossed the whole map");
    private final StatTile delayTile = new StatTile("Avg delay (s)", "Extra time compared with an empty road, ordinary vehicles");
    private final StatTile redTile = new StatTile("Waiting at red", "Vehicle threads blocked in wait() at a red light");
    private final StatTile throughputTile = new StatTile("Throughput /min", "Trips completed per simulated minute");
    private final StatTile emergencyTile = new StatTile("Emerg. trips", "Ambulances and fire engines that crossed the map");
    private final StatTile emergencyDelayTile = new StatTile("Emerg. delay (s)", "Average delay of emergency vehicles");
    private final StatTile preemptTile = new StatTile("Pre-emptions", "Times a junction gave right of way to an emergency vehicle");
    private final StatTile awayTile = new StatTile("Turned away", "Arrivals dropped because the queue at a road entry was full");
    private final JPanel junctionList = new JPanel(new GridLayout(0, 1, 0, 4));
    private final List<JLabel> junctionLabels = new ArrayList<JLabel>();

    private volatile SimulationEngine engine;
    private boolean loadingScenarios;

    public LiveSimulationPanel(AppContext ctx, MainFrame frame) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.frame = frame;
        this.canvas = new SimulationCanvas(this);
        setBackground(Theme.PANEL);

        add(buildToolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        add(buildLog(), BorderLayout.SOUTH);

        // Repaint the map ~30 times a second and refresh the numbers 4 times a second.
        new Timer(33, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.repaint();
            }
        }).start();
        new Timer(250, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshStats();
            }
        }).start();

        reloadScenarios();
        updateControls();
    }

    // ----- layout -----

    private JPanel buildToolbar() {
        JPanel rows = new JPanel(new GridLayout(2, 1));
        rows.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        scenarioBox.setPrototypeDisplayValue(new Scenario("Hospital Road - Frequent Emergencies xx"));
        row1.add(label("Scenario"));
        row1.add(scenarioBox);
        row1.add(label("Signals"));
        row1.add(signalBox);
        row1.add(label("Emergency priority"));
        row1.add(priorityBox);
        row1.add(label("Speed"));
        row1.add(speedBox);
        rows.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        startButton.setBackground(Theme.BRAND);
        startButton.setForeground(Color.WHITE);
        row2.add(startButton);
        row2.add(pauseButton);
        row2.add(stopButton);
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 22));
        row2.add(sep);
        row2.add(ambulanceButton);
        row2.add(fireButton);
        modeHint.setFont(Theme.SMALL);
        modeHint.setForeground(Theme.MUTED);
        row2.add(modeHint);
        rows.add(row2);

        // Buttons must not steal keyboard focus from the map, or the shortcuts stop working.
        for (Component c : new Component[]{startButton, pauseButton, stopButton, ambulanceButton, fireButton,
                scenarioBox, signalBox, priorityBox, speedBox}) {
            c.setFocusable(false);
        }

        startButton.addActionListener(e -> startRun());
        pauseButton.addActionListener(e -> togglePause());
        stopButton.addActionListener(e -> stopRun());
        ambulanceButton.addActionListener(e -> dispatch(VehicleFactory.AMBULANCE, null));
        fireButton.addActionListener(e -> dispatch(VehicleFactory.FIRE_ENGINE, null));
        speedBox.addActionListener(e -> {
            SimulationEngine eng = engine;
            if (eng != null) {
                eng.setSpeed(SPEEDS[speedBox.getSelectedIndex()]);
            }
        });
        scenarioBox.addActionListener(e -> scenarioChosen());
        priorityBox.addActionListener(e -> updateModeHint());
        return rows;
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Theme.PANEL);
        side.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        side.setPreferredSize(new Dimension(270, 10));

        JLabel title = new JLabel("Live statistics");
        title.setFont(Theme.HEADING);
        title.setAlignmentX(LEFT_ALIGNMENT);
        side.add(title);
        side.add(Box.createVerticalStrut(6));

        JPanel tiles = new JPanel(new GridLayout(0, 2, 6, 6));
        tiles.setOpaque(false);
        for (StatTile t : new StatTile[]{timeTile, onRoadTile, tripsTile, delayTile, redTile, throughputTile,
                emergencyTile, emergencyDelayTile, preemptTile, awayTile}) {
            tiles.add(t);
        }
        tiles.setAlignmentX(LEFT_ALIGNMENT);
        tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));
        side.add(tiles);
        side.add(Box.createVerticalStrut(10));

        JLabel jt = new JLabel("Junctions (main / cross)");
        jt.setFont(Theme.HEADING);
        jt.setAlignmentX(LEFT_ALIGNMENT);
        side.add(jt);
        side.add(Box.createVerticalStrut(4));
        junctionList.setOpaque(false);
        junctionList.setAlignmentX(LEFT_ALIGNMENT);
        side.add(junctionList);
        side.add(Box.createVerticalGlue());
        return side;
    }

    private JScrollPane buildLog() {
        logArea.setEditable(false);
        logArea.setFont(Theme.MONO);
        logArea.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                "Event log"));
        return scroll;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.BODY);
        return l;
    }

    // ----- scenarios -----

    /** Reloads the scenario list (after a database change or an edit), keeping the selection. */
    public void reloadScenarios() {
        Scenario current = (Scenario) scenarioBox.getSelectedItem();
        int keepId = current == null ? -1 : current.getId();
        List<Scenario> list;
        try {
            list = ctx.getScenarios().findAll();
        } catch (RepositoryException e) {
            appendLog("Could not load scenarios: " + e.getMessage());
            return;
        }
        loadingScenarios = true;
        scenarioBox.removeAllItems();
        Scenario select = null;
        for (Scenario s : list) {
            scenarioBox.addItem(s);
            if (s.getId() == keepId) {
                select = s;
            }
        }
        loadingScenarios = false;
        if (select != null) {
            scenarioBox.setSelectedItem(select);
        } else if (scenarioBox.getItemCount() > 0) {
            scenarioBox.setSelectedIndex(0);
        }
        scenarioChosen();
    }

    /** Picks a scenario from another tab ("Use in simulation"). */
    public void selectScenario(int id) {
        for (int i = 0; i < scenarioBox.getItemCount(); i++) {
            if (scenarioBox.getItemAt(i).getId() == id) {
                scenarioBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void scenarioChosen() {
        if (loadingScenarios) {
            return;
        }
        Scenario s = (Scenario) scenarioBox.getSelectedItem();
        if (s == null || (engine != null && engine.isRunning())) {
            return;
        }
        signalBox.setSelectedItem(s.getSignalMode());
        priorityBox.setSelectedItem(s.getPriorityMode());
        canvas.setPreview(s.getJunctionCount());
        scenarioBox.setToolTipText(s.getDescription());
        updateModeHint();
    }

    private void updateModeHint() {
        PriorityMode mode = (PriorityMode) priorityBox.getSelectedItem();
        modeHint.setText(mode == null ? "" : "   " + mode.getDescription());
    }

    // ----- SimulationCanvas.Controls -----

    @Override
    public void startRun() {
        if (engine != null && engine.isRunning()) {
            return;
        }
        Scenario s = (Scenario) scenarioBox.getSelectedItem();
        if (s == null) {
            return;
        }
        SimulationEngine e;
        try {
            e = new SimulationEngine(s, (SignalMode) signalBox.getSelectedItem(),
                    (PriorityMode) priorityBox.getSelectedItem(), ctx.getRunDao(), this);
        } catch (InvalidScenarioException ex) {
            frame.showError("This scenario cannot run", ex.getMessage());
            return;
        }
        logArea.setText("");
        e.setSpeed(SPEEDS[speedBox.getSelectedIndex()]);
        engine = e;
        canvas.setEngine(e);
        e.start();
        rebuildJunctionList(e.getNetwork());
        updateControls();
        canvas.requestFocusInWindow();
    }

    @Override
    public void stopRun() {
        SimulationEngine e = engine;
        if (e != null && e.isRunning()) {
            e.stop(false);
            updateControls();
        }
    }

    @Override
    public void togglePause() {
        SimulationEngine e = engine;
        if (e == null || !e.isRunning()) {
            return;
        }
        if (e.isPaused()) {
            e.resume();
        } else {
            e.pause();
        }
        updateControls();
    }

    @Override
    public void dispatch(String typeCode, Lane lane) {
        SimulationEngine e = engine;
        if (e == null || !e.isRunning()) {
            appendLog("Start a simulation first, then send emergency vehicles.");
            return;
        }
        RoadNetwork net = e.getNetwork();
        if (lane == null) {
            lane = VehicleFactory.AMBULANCE.equals(typeCode) ? net.getMainLane(Direction.EAST)
                    : net.getCrossLane(Direction.SOUTH, net.getJunctions().size() / 2);
        }
        try {
            e.dispatchEmergency(typeCode, lane);
        } catch (IllegalSimulationStateException ex) {
            appendLog(ex.getMessage());
        }
    }

    @Override
    public void setSpeedIndex(int index) {
        speedBox.setSelectedIndex(index);
    }

    // ----- EngineListener (called from simulation threads) -----

    @Override
    public void onLog(final String message) {
        SwingUtilities.invokeLater(() -> appendLog(message));
    }

    @Override
    public void onRunFinished(final RunSummary summary) {
        SwingUtilities.invokeLater(() -> {
            refreshStats();
            engine = null;
            canvas.setEngine(null);
            updateControls();
            frame.runFinished(summary);
        });
    }

    // ----- updates on the event dispatch thread -----

    private void appendLog(String line) {
        logArea.append(line + "\n");
        int extra = logArea.getLineCount() - MAX_LOG_LINES;
        if (extra > 0) {
            try {
                logArea.replaceRange("", 0, logArea.getLineEndOffset(extra - 1));
            } catch (BadLocationException ignored) {
                // cannot happen: the offsets come from the text area itself
            }
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void updateControls() {
        SimulationEngine e = engine;
        boolean running = e != null && e.isRunning();
        startButton.setEnabled(!running);
        pauseButton.setEnabled(running);
        stopButton.setEnabled(running);
        ambulanceButton.setEnabled(running);
        fireButton.setEnabled(running);
        scenarioBox.setEnabled(!running);
        signalBox.setEnabled(!running);
        priorityBox.setEnabled(!running);
        pauseButton.setText(running && e.isPaused() ? "Resume" : "Pause");
    }

    private void refreshStats() {
        SimulationEngine e = engine;
        if (e == null) {
            return;
        }
        long now = e.getClock().now();
        StatsCollector.Snapshot s = e.getStats().snapshot(now);
        long sec = now / 1000;
        timeTile.setValue(String.format("%02d:%02d", sec / 60, sec % 60));
        onRoadTile.setValue(String.valueOf(e.getVehicles().size()));
        tripsTile.setValue(String.valueOf(s.tripsCompleted));
        delayTile.setValue(String.format("%.1f", s.avgDelaySec));
        redTile.setValue(String.valueOf(SimulationCanvas.countWaitingAtRed(e)), Theme.STATE_WAITING);
        throughputTile.setValue(String.format("%.1f", s.throughputPerMin));
        emergencyTile.setValue(String.valueOf(s.emergencyTrips), Theme.SERIES_EMERGENCY);
        emergencyDelayTile.setValue(s.emergencyTrips == 0 ? "-" : String.format("%.1f", s.emergencyAvgDelaySec),
                Theme.SERIES_EMERGENCY);
        preemptTile.setValue(String.valueOf(s.preemptions));
        awayTile.setValue(String.valueOf(s.vehiclesTurnedAway), s.vehiclesTurnedAway > 0 ? Theme.DANGER : Theme.TEXT);

        List<Junction> junctions = e.getNetwork().getJunctions();
        for (int i = 0; i < junctions.size() && i < junctionLabels.size(); i++) {
            Junction j = junctions.get(i);
            SignalController sig = j.getSignal();
            JLabel l = junctionLabels.get(i);
            l.setIcon(new SignalPairIcon(sig.getLight(Axis.EW), sig.getLight(Axis.NS)));
            String holder = sig.getPriorityHolder();
            l.setText(String.format("<html><b>%s</b> &nbsp;%s %.0f s<br><font color='#5F6B66'>queued %d / %d%s</font></html>",
                    j.getName(), sig.getPhase().describe(), sig.getPhaseElapsedMs(now) / 1000.0,
                    SimulationCanvas.queued(j, Axis.EW), SimulationCanvas.queued(j, Axis.NS),
                    holder == null ? "" : " &nbsp;<font color='#1B7F4B'><b>priority: " + holder + "</b></font>"));
        }
        frame.setLiveThreads(e.getRegistry().liveCount());
    }

    private void rebuildJunctionList(RoadNetwork net) {
        junctionList.removeAll();
        junctionLabels.clear();
        for (int i = 0; i < net.getJunctions().size(); i++) {
            JLabel l = new JLabel();
            l.setFont(Theme.BODY);
            l.setOpaque(true);
            l.setBackground(Theme.CARD);
            l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            l.setIconTextGap(8);
            junctionLabels.add(l);
            junctionList.add(l);
        }
        junctionList.revalidate();
        junctionList.repaint();
    }

    SimulationCanvas getCanvas() {
        return canvas;
    }

    public SimulationEngine getEngine() {
        return engine;
    }

    public boolean isRunning() {
        SimulationEngine e = engine;
        return e != null && e.isRunning();
    }

    /**
     * Two traffic lights side by side: main road and cross street.
     * Implements the Swing Icon interface so a JLabel can show it.
     */
    private static class SignalPairIcon implements Icon {
        private final SignalLight main;
        private final SignalLight cross;

        SignalPairIcon(SignalLight main, SignalLight cross) {
            this.main = main;
            this.cross = cross;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw(g, x, y, main);
            draw(g, x + 14, y, cross);
            g.dispose();
        }

        private static void draw(Graphics2D g, int x, int y, SignalLight light) {
            g.setColor(Theme.HOUSING);
            g.fillRoundRect(x, y, 11, 30, 6, 6);
            dot(g, x, y + 2, Theme.LIGHT_RED, light == SignalLight.RED);
            dot(g, x, y + 11, Theme.LIGHT_YELLOW, light == SignalLight.YELLOW);
            dot(g, x, y + 20, Theme.LIGHT_GREEN, light == SignalLight.GREEN);
        }

        private static void dot(Graphics2D g, int x, int y, Color c, boolean on) {
            g.setColor(on ? c : new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
            g.fillOval(x + 2, y, 7, 7);
        }

        @Override
        public int getIconWidth() {
            return 25;
        }

        @Override
        public int getIconHeight() {
            return 30;
        }
    }
}
