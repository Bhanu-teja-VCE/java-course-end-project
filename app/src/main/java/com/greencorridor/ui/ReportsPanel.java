package com.greencorridor.ui;

import com.greencorridor.db.ModeStat;
import com.greencorridor.db.RunDao;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.io.CsvExporter;
import com.greencorridor.model.PriorityMode;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The "Reports" tab: every stored run (JTable), a chart comparing the priority modes
 * (data from the stored procedure sp_mode_comparison), details, delete and CSV export.
 * Without a database it shows an explanation instead (CardLayout).
 */
public class ReportsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final AppContext ctx;
    private final MainFrame frame;
    private final CardLayout cards = new CardLayout();
    private final DefaultTableModel runsModel = new DefaultTableModel(RunDao.RUN_COLUMNS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable runsTable = new JTable(runsModel);
    private final JComboBox<String> filterBox = new JComboBox<String>(new String[]{"ALL", "FIXED", "ADAPTIVE"});
    private final BarChartPanel chart = new BarChartPanel();

    public ReportsPanel(AppContext ctx, MainFrame frame) {
        this.ctx = ctx;
        this.frame = frame;
        setLayout(cards);
        add(buildOnline(), "ONLINE");
        add(buildOffline(), "OFFLINE");
    }

    private JPanel buildOnline() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel title = new JLabel("Saved runs   ");
        title.setFont(Theme.TITLE);
        toolbar.add(title);
        toolbar.add(button("Refresh", e -> refresh()));
        toolbar.add(button("Details...", e -> showDetails()));
        toolbar.add(button("Delete run", e -> deleteRun()));
        toolbar.add(button("Export CSV...", e -> exportCsv()));
        toolbar.add(new JLabel("   Chart for signals:"));
        filterBox.addActionListener(e -> refreshChart());
        toolbar.add(filterBox);
        panel.add(toolbar, BorderLayout.NORTH);

        runsTable.setRowHeight(22);
        runsTable.setAutoCreateRowSorter(true);
        runsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runsTable.getColumnModel().getColumn(1).setPreferredWidth(240);
        runsTable.getColumnModel().getColumn(5).setPreferredWidth(140);
        runsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showDetails();
                }
            }
        });
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(runsTable), chart);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildOffline() {
        JPanel panel = new JPanel(new GridBagLayout());
        JPanel box = new JPanel(new BorderLayout(0, 10));
        JLabel text = new JLabel("<html><div style='width:420px'><b>Reports need the MySQL database.</b><br><br>"
                + "Every run is stored in the tables simulation_run, vehicle_trip and preemption_event, and the "
                + "comparison chart is computed by the stored procedure sp_mode_comparison. Connect to MySQL to "
                + "see them. Until then, each run is still written to a text log in the logs folder.</div></html>",
                SwingConstants.LEFT);
        text.setFont(Theme.BODY);
        box.add(text, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(button("Connection settings...", e -> frame.openDbSettings()));
        box.add(buttons, BorderLayout.SOUTH);
        panel.add(box);
        return panel;
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    /** Reloads runs and the chart, or shows the offline card. */
    public void refresh() {
        RunDao dao = ctx.getRunDao();
        if (dao == null) {
            cards.show(this, "OFFLINE");
            return;
        }
        cards.show(this, "ONLINE");
        try {
            List<Object[]> rows = dao.listRuns();
            runsModel.setRowCount(0);
            for (Object[] row : rows) {
                runsModel.addRow(row);
            }
        } catch (RepositoryException e) {
            frame.showError("Could not load the runs", e.getMessage());
            return;
        }
        refreshChart();
    }

    private void refreshChart() {
        RunDao dao = ctx.getRunDao();
        if (dao == null) {
            return;
        }
        String filter = (String) filterBox.getSelectedItem();
        try {
            List<ModeStat> stats = dao.compareModes(filter);
            chart.setData(stats, subtitle(stats, filter));
        } catch (RepositoryException e) {
            frame.showError("Could not compare the modes", e.getMessage());
        }
    }

    /** One sentence with the headline result, e.g. "Green corridor cut emergency delay by 87%". */
    private static String subtitle(List<ModeStat> stats, String filter) {
        String scope = "completed runs, " + ("ALL".equals(filter) ? "all signal types" : filter.toLowerCase() + " signals");
        ModeStat none = null;
        ModeStat corridor = null;
        for (ModeStat m : stats) {
            if (m.getMode() == PriorityMode.NONE) {
                none = m;
            } else if (m.getMode() == PriorityMode.CORRIDOR) {
                corridor = m;
            }
        }
        if (none != null && corridor != null && none.getEmergencyAvgDelaySec() != null
                && corridor.getEmergencyAvgDelaySec() != null && none.getEmergencyAvgDelaySec() > 0) {
            double cut = 100 * (1 - corridor.getEmergencyAvgDelaySec() / none.getEmergencyAvgDelaySec());
            return String.format("From stored procedure sp_mode_comparison, %s. The green corridor cut emergency "
                    + "vehicle delay by %.0f%% compared with no priority.", scope, cut);
        }
        return "From stored procedure sp_mode_comparison, " + scope + ".";
    }

    private int selectedRunId() {
        int row = runsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a run in the table first.", "No run selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return -1;
        }
        return (Integer) runsModel.getValueAt(runsTable.convertRowIndexToModel(row), 0);
    }

    /** Opens the details of a table row (used by the screenshot tool). */
    void showDetailsOfRow(int row) {
        runsTable.setRowSelectionInterval(row, row);
        showDetails();
    }

    private void showDetails() {
        int id = selectedRunId();
        if (id < 0) {
            return;
        }
        int row = runsTable.convertRowIndexToModel(runsTable.getSelectedRow());
        try {
            new RunDetailsDialog(frame, ctx.getRunDao(), id, String.valueOf(runsModel.getValueAt(row, 1))).setVisible(true);
        } catch (RepositoryException e) {
            frame.showError("Could not load the run", e.getMessage());
        }
    }

    private void deleteRun() {
        int id = selectedRunId();
        if (id < 0) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Delete run #" + id + " with all its trips and pre-emptions?",
                "Delete run", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ctx.getRunDao().deleteRun(id);
            refresh();
        } catch (RepositoryException e) {
            frame.showError("Could not delete the run", e.getMessage());
        }
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setSelectedFile(new File("green-corridor-runs.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            CsvExporter.export(runsModel, fc.getSelectedFile());
            JOptionPane.showMessageDialog(this, runsModel.getRowCount() + " runs written to "
                    + fc.getSelectedFile().getAbsolutePath(), "Exported", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            frame.showError("Export failed", e.getMessage());
        }
    }
}
