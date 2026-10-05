package com.greencorridor.ui;

import com.greencorridor.db.ModeStat;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Grouped bar chart drawn with Graphics2D: for each emergency-priority mode, the average
 * delay of emergency vehicles and of ordinary traffic. Lower is better.
 */
public class BarChartPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private List<ModeStat> data = new ArrayList<ModeStat>();
    private String subtitle = "";

    public BarChartPanel() {
        setBackground(Theme.CARD);
        setPreferredSize(new Dimension(600, 260));
    }

    public void setData(List<ModeStat> data, String subtitle) {
        this.data = data;
        this.subtitle = subtitle;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g.setColor(Theme.TEXT);
        g.setFont(Theme.HEADING);
        g.drawString("Average delay by emergency-priority mode (lower is better)", 16, 22);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        g.drawString(subtitle, 16, 40);

        if (data.isEmpty()) {
            g.setFont(Theme.BODY);
            g.drawString("No completed runs yet. Run the three \"Morning Peak\" scenarios to the end to compare the modes.", 16, h / 2);
            g.dispose();
            return;
        }

        int left = 56;
        int right = w - 170;
        int top = 58;
        int bottom = h - 46;
        double max = 1;
        for (ModeStat m : data) {
            max = Math.max(max, value(m.getEmergencyAvgDelaySec()));
            max = Math.max(max, value(m.getAvgDelaySec()));
        }
        double step = niceStep(max / 4);
        double axisMax = Math.ceil(max / step) * step;

        // grid lines and y labels
        g.setFont(Theme.SMALL);
        FontMetrics fm = g.getFontMetrics();
        for (double v = 0; v <= axisMax + 1e-9; v += step) {
            double y = bottom - (bottom - top) * v / axisMax;
            g.setColor(new Color(0xE6EAE8));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(left, y, right, y));
            g.setColor(Theme.MUTED);
            String label = String.format("%.0f s", v);
            g.drawString(label, left - 8 - fm.stringWidth(label), (float) y + 4);
        }

        int groups = data.size();
        double groupWidth = (right - left) / (double) groups;
        double barWidth = Math.min(46, groupWidth / 3.2);
        for (int i = 0; i < groups; i++) {
            ModeStat m = data.get(i);
            double cx = left + groupWidth * (i + 0.5);
            bar(g, cx - barWidth - 3, barWidth, bottom, top, axisMax, m.getEmergencyAvgDelaySec(), Theme.SERIES_EMERGENCY);
            bar(g, cx + 3, barWidth, bottom, top, axisMax, m.getAvgDelaySec(), Theme.SERIES_GENERAL);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.HEADING);
            String name = m.getMode().getLabel();
            g.drawString(name, (float) (cx - g.getFontMetrics().stringWidth(name) / 2.0), bottom + 18);
            g.setFont(Theme.SMALL);
            String runs = m.getRuns() + (m.getRuns() == 1 ? " run" : " runs");
            g.setColor(Theme.MUTED);
            g.drawString(runs, (float) (cx - g.getFontMetrics().stringWidth(runs) / 2.0), bottom + 33);
        }
        g.setColor(Theme.BORDER);
        g.draw(new Line2D.Double(left, bottom, right, bottom));

        // legend
        int lx = right + 24;
        legend(g, lx, top + 4, Theme.SERIES_EMERGENCY, "Emergency vehicles");
        legend(g, lx, top + 26, Theme.SERIES_GENERAL, "Ordinary traffic");
        g.dispose();
    }

    private static double value(Double d) {
        return d == null ? 0 : d;
    }

    private static void bar(Graphics2D g, double x, double width, int bottom, int top, double axisMax,
                            Double value, Color color) {
        if (value == null) {
            return;
        }
        double height = (bottom - top) * value / axisMax;
        g.setColor(color);
        g.fill(new Rectangle2D.Double(x, bottom - height, width, height));
        g.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        String label = String.format("%.1f", value);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        g.drawString(label, (float) (x + width / 2 - fm.stringWidth(label) / 2.0), (float) (bottom - height - 4));
    }

    private static void legend(Graphics2D g, int x, int y, Color color, String text) {
        g.setColor(color);
        g.fillRect(x, y, 12, 12);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.SMALL);
        g.drawString(text, x + 18, y + 10);
    }

    /** 1, 2, 5, 10, 20, 50... the nearest "round" step at or above raw. */
    private static double niceStep(double raw) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 0.1))));
        double[] steps = {1, 2, 5, 10};
        for (double s : steps) {
            if (s * magnitude >= raw) {
                return s * magnitude;
            }
        }
        return 10 * magnitude;
    }
}
