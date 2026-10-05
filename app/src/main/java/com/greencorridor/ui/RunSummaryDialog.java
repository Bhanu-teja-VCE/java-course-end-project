package com.greencorridor.ui;

import com.greencorridor.model.RunSummary;
import com.greencorridor.model.TripRecord;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Map;

/** Shown when a run ends: the key figures, delay per vehicle type and where it was saved. */
public class RunSummaryDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    public RunSummaryDialog(final MainFrame frame, RunSummary s, boolean online) {
        super(frame, "Run " + s.getStatus().toLowerCase(), true);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel("<html><b style='font-size:13px'>" + s.getScenarioName() + "</b><br>"
                + s.getSignalMode().getLabel() + " signals, " + s.getPriorityMode().getLabel() + ", "
                + s.getSimSeconds() + " simulated seconds (" + s.getStatus().toLowerCase() + ")</html>");
        content.add(title, BorderLayout.NORTH);

        JPanel tiles = new JPanel(new GridLayout(0, 4, 6, 6));
        tiles.add(tile("Trips completed", String.valueOf(s.getTripsCompleted())));
        tiles.add(tile("Avg delay (s)", String.format("%.1f", s.getAvgDelaySec())));
        tiles.add(tile("95% of trips under (s)", String.format("%.1f", s.getP95DelaySec())));
        tiles.add(tile("Throughput /min", String.format("%.1f", s.getThroughputPerMin())));
        tiles.add(tile("Emergency trips", String.valueOf(s.getEmergencyTrips())));
        tiles.add(tile("Emergency delay (s)", s.getEmergencyTrips() == 0 ? "-"
                : String.format("%.1f", s.getEmergencyAvgDelaySec())));
        tiles.add(tile("Pre-emptions", String.valueOf(s.getPreemptions())));
        tiles.add(tile("Avg time to green (s)", s.getPreemptions() == 0 ? "-"
                : String.format("%.1f", s.getAvgResponseSec())));

        DefaultTableModel model = new DefaultTableModel(new Object[]{"Vehicle type", "Trips", "Avg delay (s)"}, 0);
        for (Map.Entry<String, double[]> e : s.getByType().entrySet()) {
            model.addRow(new Object[]{e.getKey(), (int) e.getValue()[0], String.format("%.1f", e.getValue()[1])});
        }
        JTable table = new JTable(model);
        table.setEnabled(false);
        table.setRowHeight(20);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 170));

        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.add(tiles, BorderLayout.NORTH);
        middle.add(scroll, BorderLayout.CENTER);
        if (!s.getMostDelayed().isEmpty()) {
            StringBuffer worst = new StringBuffer("<html><b>Most delayed:</b> ");
            for (int i = 0; i < s.getMostDelayed().size(); i++) {
                TripRecord t = s.getMostDelayed().get(i);
                worst.append(i == 0 ? "" : ", ").append(t.getVehicleType().charAt(0))
                        .append(t.getVehicleType().substring(1).toLowerCase()).append(" #").append(t.getVehicleNo())
                        .append(String.format(" (%.1f s, %s)", t.getDelaySec(), t.getDirection().getLabel().toLowerCase()));
            }
            JLabel worstLabel = new JLabel(worst.append("</html>").toString());
            worstLabel.setFont(Theme.SMALL);
            middle.add(worstLabel, BorderLayout.SOUTH);
        }
        content.add(middle, BorderLayout.CENTER);

        String saved = s.getRunId() > 0
                ? "Saved as run #" + s.getRunId() + " in MySQL. Figures checked by the stored procedure sp_finish_run."
                : "Not saved in MySQL (offline). The run's events were written to the logs folder.";
        JLabel note = new JLabel(saved);
        note.setFont(Theme.SMALL);
        note.setForeground(s.isVerifiedByDatabase() ? Theme.BRAND : Theme.MUTED);
        JPanel south = new JPanel(new BorderLayout());
        south.add(note, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        if (online && s.getRunId() > 0) {
            JButton reports = new JButton("Open Reports");
            reports.addActionListener(e -> {
                dispose();
                frame.showReports();
            });
            buttons.add(reports);
        }
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        pack();
        setLocationRelativeTo(frame);
    }

    private static JPanel tile(String caption, String value) {
        StatTile t = new StatTile(caption, null);
        t.setValue(value);
        return t;
    }
}
