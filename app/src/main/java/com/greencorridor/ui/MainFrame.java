package com.greencorridor.ui;

import com.greencorridor.db.Database;
import com.greencorridor.db.SchemaInstaller;
import com.greencorridor.exception.DatabaseUnavailableException;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.model.RunSummary;
import com.greencorridor.vehicle.VehicleFactory;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The main window: a menu bar, four tabs (Live Simulation, Thread Monitor, Scenarios,
 * Reports) and a status bar. It also connects to MySQL in the background at start-up.
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final AppContext ctx = new AppContext();
    private final JTabbedPane tabs = new JTabbedPane();
    private final LiveSimulationPanel live;
    private final ScenarioManagerPanel scenarios;
    private final ReportsPanel reports;
    private final JLabel dbStatus = new JLabel();
    private final JLabel threadStatus = new JLabel();

    public MainFrame() {
        super("GreenCorridor - Smart Traffic Junction Simulator");
        live = new LiveSimulationPanel(ctx, this);
        ThreadMonitorPanel monitor = new ThreadMonitorPanel(live);
        scenarios = new ScenarioManagerPanel(ctx, this);
        reports = new ReportsPanel(ctx, this);

        tabs.addTab("Live Simulation", live);
        tabs.addTab("Thread Monitor", monitor);
        tabs.addTab("Scenarios", scenarios);
        tabs.addTab("Reports", reports);
        tabs.setMnemonicAt(0, KeyEvent.VK_L);
        tabs.setMnemonicAt(1, KeyEvent.VK_T);
        tabs.setMnemonicAt(2, KeyEvent.VK_S);
        tabs.setMnemonicAt(3, KeyEvent.VK_R);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == reports) {
                reports.refresh();
            }
        });

        setJMenuBar(buildMenu());
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        ctx.addListener(() -> {
            live.reloadScenarios();
            scenarios.refresh();
            reports.refresh();
            updateDbStatus();
        });
        scenarios.refresh();
        reports.refresh();
        updateDbStatus();

        setIconImages(icons());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1500, screen.width), Math.min(900, screen.height - 40));
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
    }

    // ----- menu and status bar -----

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(item("Exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), e -> exit()));
        bar.add(file);

        JMenu sim = new JMenu("Simulation");
        sim.setMnemonic(KeyEvent.VK_I);
        sim.add(item("Start", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), e -> live.startRun()));
        sim.add(item("Pause / Resume", KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), e -> live.togglePause()));
        sim.add(item("Stop", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), e -> live.stopRun()));
        sim.addSeparator();
        sim.add(item("Send ambulance", KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0),
                e -> live.dispatch(VehicleFactory.AMBULANCE, null)));
        sim.add(item("Send fire engine", KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0),
                e -> live.dispatch(VehicleFactory.FIRE_ENGINE, null)));
        bar.add(sim);

        JMenu db = new JMenu("Database");
        db.setMnemonic(KeyEvent.VK_D);
        db.add(item("Connection settings...", null, e -> openDbSettings()));
        db.add(item("Reconnect", null, e -> connectToDatabase(true)));
        db.add(item("Create tables and sample data...", null, e -> confirmInstall()));
        bar.add(db);

        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(item("Controls", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), e -> showControls()));
        help.add(item("About", null, e -> showAbout()));
        bar.add(help);
        return bar;
    }

    private static JMenuItem item(String text, KeyStroke key, ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (key != null) {
            item.setAccelerator(key);
        }
        item.addActionListener(action);
        return item;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        dbStatus.setFont(Theme.SMALL);
        threadStatus.setFont(Theme.SMALL);
        threadStatus.setForeground(Theme.MUTED);
        bar.add(dbStatus, BorderLayout.WEST);
        bar.add(threadStatus, BorderLayout.EAST);
        return bar;
    }

    private void updateDbStatus() {
        dbStatus.setText((ctx.isOnline() ? "● " : "○ ") + ctx.getStatus());
        dbStatus.setForeground(ctx.isOnline() ? Theme.BRAND : Theme.WARNING);
    }

    public void setLiveThreads(int count) {
        threadStatus.setText(count + " live simulation threads   |   Space pause  A ambulance  F fire engine  H help");
    }

    // ----- database connection (network work never runs on the event dispatch thread) -----

    /** Connects in a background thread. "interactive" means the user asked, so report the outcome. */
    public void connectToDatabase(final boolean interactive) {
        dbStatus.setText("Connecting to MySQL as " + Database.getConfig().describe() + "...");
        Thread t = new Thread(() -> {
            String error = null;
            boolean installed = false;
            try (Connection con = Database.getConnection()) {
                installed = SchemaInstaller.isInstalled(con);
            } catch (DatabaseUnavailableException | SQLException e) {
                error = e.getMessage();
            }
            final String err = error;
            final boolean ready = installed;
            SwingUtilities.invokeLater(() -> afterConnect(err, ready, interactive));
        }, "DB-Connect");
        t.setDaemon(true);
        t.start();
    }

    private void afterConnect(String error, boolean installed, boolean interactive) {
        if (error != null) {
            ctx.goOffline(shorten(error));
            int answer = JOptionPane.showConfirmDialog(this,
                    "<html><div style='width:420px'><b>Could not connect to MySQL.</b><br><br>" + html(error)
                    + "<br><br>The simulator works without a database, but runs will not be saved and the "
                    + "Reports tab stays empty.<br><br>Open the connection settings now?</div></html>",
                    "Working offline", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                openDbSettings();
            }
        } else if (!installed) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Connected to MySQL, but the GreenCorridor tables do not exist yet.\n"
                    + "Create the tables, stored procedures and sample scenarios now?",
                    "Set up the database", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                installSchema();
            } else {
                ctx.goOffline("tables not created (Database > Create tables)");
            }
        } else {
            ctx.goOnline(Database.getConfig().describe());
            if (interactive) {
                JOptionPane.showMessageDialog(this, "Connected to MySQL as " + Database.getConfig().describe(),
                        "Connected", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void confirmInstall() {
        if (live.isRunning()) {
            showError("Simulation running", "Stop the simulation before re-creating the tables.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "This drops and re-creates all GreenCorridor tables and procedures in "
                + Database.getConfig().getDatabase() + ".\nSaved scenarios and runs will be erased. Continue?",
                "Create tables", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            installSchema();
        }
    }

    private void installSchema() {
        dbStatus.setText("Creating tables...");
        new Thread(() -> {
            String error = null;
            int statements = 0;
            try (Connection con = Database.getConnection()) {
                statements = SchemaInstaller.install(con);
            } catch (RepositoryException | SQLException e) {
                error = e.getMessage();
            }
            final String err = error;
            final int n = statements;
            SwingUtilities.invokeLater(() -> {
                if (err != null) {
                    ctx.goOffline(shorten(err));
                    showError("Database set-up failed", err);
                } else {
                    ctx.goOnline(Database.getConfig().describe());
                    JOptionPane.showMessageDialog(this, "Database ready: " + n + " SQL statements executed.",
                            "Database ready", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }, "DB-Install").start();
    }

    private static String shorten(String message) {
        String oneLine = message.replaceAll("\\s+", " ");
        return oneLine.length() > 90 ? oneLine.substring(0, 87) + "..." : oneLine;
    }

    /**
     * Makes plain text (for example a driver error) safe inside an HTML label. JOptionPane
     * would otherwise split the message at every line break and show raw tags.
     */
    static String html(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replaceAll("\\r?\\n", "<br>");
    }

    // ----- called by the tabs -----

    public void openDbSettings() {
        new DbSettingsDialog(this).setVisible(true);
    }

    public void runFinished(RunSummary summary) {
        reports.refresh();
        new RunSummaryDialog(this, summary, ctx.isOnline()).setVisible(true);
    }

    public void scenariosChanged() {
        scenarios.refresh();
        live.reloadScenarios();
    }

    public void useScenario(int id) {
        live.selectScenario(id);
        tabs.setSelectedComponent(live);
    }

    public void showReports() {
        tabs.setSelectedComponent(reports);
    }

    // package-private accessors used by the screenshot tool in the tests
    LiveSimulationPanel getLive() {
        return live;
    }

    JTabbedPane getTabs() {
        return tabs;
    }

    ScenarioManagerPanel getScenarioPanel() {
        return scenarios;
    }

    ReportsPanel getReportsPanel() {
        return reports;
    }

    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, "<html><div style='width:400px'>" + html(message) + "</div></html>", title,
                JOptionPane.ERROR_MESSAGE);
    }

    private void showControls() {
        JOptionPane.showMessageDialog(this, "<html><b>Live Simulation tab</b><br>"
                + "Enter / F5: start &nbsp; Esc / F7: stop &nbsp; Space / F6: pause<br>"
                + "A / F8: ambulance to City Hospital &nbsp; F / F9: fire engine<br>"
                + "1-4: speed x1, x2, x4, x8 &nbsp; H: help on the map<br><br>"
                + "<b>Mouse on the map</b><br>"
                + "Hover a vehicle: its thread, state and priority. Click it to keep it selected.<br>"
                + "Click a junction: end its green early (traffic police override).<br>"
                + "Click a road entry arrow: send an ambulance there (Shift+click: fire engine).</html>",
                "Controls", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this, "<html><b>GreenCorridor</b> - Smart Traffic Junction Simulator<br>"
                + "with an emergency vehicle green corridor.<br><br>"
                + "Course End Project, Object Oriented Programming through Java (VCE-R25)<br>"
                + "Java Swing, multithreading, collections, file I/O and JDBC with MySQL.</html>",
                "About GreenCorridor", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exit() {
        if (live.isRunning()) {
            int answer = JOptionPane.showConfirmDialog(this, "A simulation is running. Stop it and exit?", "Exit",
                    JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            live.stopRun();
        }
        dispose();
        System.exit(0);
    }

    /** A small traffic light drawn in code, used as the window icon. */
    private static List<Image> icons() {
        List<Image> list = new ArrayList<Image>();
        for (int size : new int[]{16, 32, 64}) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float u = size / 16f;
            g.setColor(Theme.BRAND);
            g.fillRoundRect(0, 0, size, size, (int) (5 * u), (int) (5 * u));
            g.setColor(Theme.HOUSING);
            g.fillRoundRect((int) (5 * u), (int) (1.5 * u), (int) (6 * u), (int) (13 * u), (int) (3 * u), (int) (3 * u));
            Color[] lamps = {new Color(0x5A2A28), new Color(0x5A5128), Theme.LIGHT_GREEN};
            for (int i = 0; i < 3; i++) {
                g.setColor(lamps[i]);
                g.fillOval((int) (6 * u), (int) ((2.5 + i * 4) * u), (int) (4 * u), (int) (4 * u));
            }
            g.setStroke(new BasicStroke(u));
            g.dispose();
            list.add(img);
        }
        return list;
    }
}
