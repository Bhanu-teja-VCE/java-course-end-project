package com.greencorridor.ui;

import com.greencorridor.engine.SimConfig;
import com.greencorridor.engine.SimulationEngine;
import com.greencorridor.vehicle.Vehicle;
import com.greencorridor.vehicle.VehicleFactory;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Drives the real application and saves screenshots for the report. Run under a virtual
 * display, against a freshly created database:
 * xvfb-run -s "-screen 0 1600x1000x24" java -cp ... com.greencorridor.ui.ScreenshotTour out-dir
 */
public class ScreenshotTour {

    private static MainFrame frame;
    private static Robot robot;
    private static File out;

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "screenshots");
        out.mkdirs();
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                UIManager.setLookAndFeel(info.getClassName());
                UIManager.put("nimbusBase", Theme.BRAND_DARK);
                UIManager.put("nimbusSelectionBackground", Theme.BRAND);
                UIManager.put("control", Theme.PANEL);
            }
        }
        robot = new Robot();
        SwingUtilities.invokeAndWait(() -> {
            frame = new MainFrame();
            frame.setBounds(0, 0, 1500, 900);
            frame.setVisible(true);
            frame.connectToDatabase(false);
        });
        Thread.sleep(3500);
        shot("01-start.png");

        // three full runs, one per priority mode, to fill the Reports chart
        for (int id : new int[]{3, 2, 1}) {
            runToEnd(id);
        }

        // a watched run of the green corridor scenario
        SwingUtilities.invokeAndWait(() -> {
            frame.getLive().selectScenario(1);
            frame.getLive().setSpeedIndex(1);
            frame.getLive().startRun();
        });
        Thread.sleep(13000);
        shot("02-traffic.png");
        SwingUtilities.invokeAndWait(() -> frame.getLive().dispatch(VehicleFactory.AMBULANCE, null));
        Thread.sleep(2600);
        shot("03-corridor-before-arrival.png");
        Thread.sleep(2200);
        hoverEmergency();
        Thread.sleep(400);
        shot("04-ambulance-passing.png");
        robot.mouseMove(1400, 960);

        SwingUtilities.invokeAndWait(() -> frame.getTabs().setSelectedIndex(1));
        Thread.sleep(1500);
        shot("05-thread-monitor.png");
        // let the ambulances reach the hospital before stopping, so the summary has them
        for (int i = 0; i < 40; i++) {
            final int[] done = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                SimulationEngine e = frame.getLive().getEngine();
                done[0] = e == null ? 99 : e.getStats().snapshot(e.getClock().now()).emergencyTrips;
            });
            if (done[0] >= 2) {
                break;
            }
            Thread.sleep(250);
        }
        SwingUtilities.invokeAndWait(() -> {
            frame.getTabs().setSelectedIndex(0);
            frame.getLive().stopRun();
        });
        Thread.sleep(3000);
        shot("06-run-summary.png");
        closeDialogs();

        SwingUtilities.invokeAndWait(() -> frame.getTabs().setSelectedIndex(2));
        Thread.sleep(800);
        shot("07-scenarios.png");
        SwingUtilities.invokeAndWait(() -> frame.getScenarioPanel().editRow(0));
        Thread.sleep(800);
        shot("08-scenario-form.png");

        SwingUtilities.invokeAndWait(() -> frame.getTabs().setSelectedIndex(3));
        Thread.sleep(1500);
        shot("09-reports.png");
        SwingUtilities.invokeLater(() -> frame.getReportsPanel().showDetailsOfRow(1));
        Thread.sleep(1500);
        shot("10-run-details.png");
        closeDialogs();

        // local-sensor mode shows the detector loops on the road
        SwingUtilities.invokeAndWait(() -> {
            frame.getTabs().setSelectedIndex(0);
            frame.getLive().selectScenario(2);
            frame.getLive().setSpeedIndex(1);
            frame.getLive().startRun();
        });
        Thread.sleep(9000);
        SwingUtilities.invokeAndWait(() -> frame.getLive().dispatch(VehicleFactory.AMBULANCE, null));
        Thread.sleep(5200);
        shot("11-local-sensor.png");
        SwingUtilities.invokeAndWait(() -> frame.getLive().stopRun());
        Thread.sleep(2500);
        closeDialogs();
        System.exit(0);
    }

    private static void runToEnd(int scenarioId) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.getLive().selectScenario(scenarioId);
            frame.getLive().setSpeedIndex(3);
            frame.getLive().startRun();
        });
        Thread.sleep(2000);
        while (true) {
            final boolean[] running = new boolean[1];
            SwingUtilities.invokeAndWait(() -> running[0] = frame.getLive().isRunning());
            if (!running[0]) {
                break;
            }
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        System.out.println("finished scenario " + scenarioId);
        closeDialogs();
    }

    /** Moves the real mouse over an emergency vehicle so the canvas shows its tooltip. */
    private static void hoverEmergency() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SimulationCanvas canvas = frame.getLive().getCanvas();
            SimulationEngine e = canvas.getEngine();
            if (e == null) {
                return;
            }
            for (Vehicle v : e.getVehicles()) {
                if (v.isEmergency()) {
                    double scale = Math.min(canvas.getWidth() / (double) SimConfig.WORLD_WIDTH,
                            canvas.getHeight() / (double) SimConfig.WORLD_HEIGHT);
                    double ox = (canvas.getWidth() - SimConfig.WORLD_WIDTH * scale) / 2;
                    double oy = (canvas.getHeight() - SimConfig.WORLD_HEIGHT * scale) / 2;
                    Point p = canvas.getLocationOnScreen();
                    robot.mouseMove((int) (p.x + ox + v.getCenterX() * scale), (int) (p.y + oy + v.getCenterY() * scale));
                    return;
                }
            }
        });
    }

    private static void closeDialogs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible()) {
                    w.dispose();
                }
            }
        });
        Thread.sleep(500);
    }

    private static void shot(String name) throws Exception {
        Rectangle r = frame.getBounds();
        BufferedImage img = robot.createScreenCapture(r);
        ImageIO.write(img, "png", new File(out, name));
        System.out.println("saved " + name);
    }
}
