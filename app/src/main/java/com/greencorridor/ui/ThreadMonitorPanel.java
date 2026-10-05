package com.greencorridor.ui;

import com.greencorridor.engine.SimulationEngine;
import com.greencorridor.engine.ThreadRegistry;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shows every thread of the running simulation and its current state, refreshed twice a
 * second, so the thread life cycle can be watched live:
 * NEW (arrived, waiting to enter) -> RUNNABLE -> TIMED_WAITING (sleep between steps)
 * / WAITING (wait() at a red light) / BLOCKED (waiting for a lock) -> TERMINATED.
 */
public class ThreadMonitorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final LiveSimulationPanel live;
    private final ThreadTableModel model = new ThreadTableModel();
    private final Map<Thread.State, JLabel> counters = new EnumMap<Thread.State, JLabel>(Thread.State.class);
    private final JCheckBox freeze = new JCheckBox("Freeze table");
    private final JLabel total = new JLabel();

    public ThreadMonitorPanel(LiveSimulationPanel live) {
        super(new BorderLayout(0, 6));
        this.live = live;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        JLabel intro = new JLabel("<html><b>Every vehicle, signal controller and helper runs on its own thread.</b> "
                + "A vehicle sleeps between steps (TIMED_WAITING). At a red light it calls <i>wait()</i> on the "
                + "junction's signal and shows as WAITING until the controller calls <i>notifyAll()</i>. "
                + "Vehicles that have arrived but cannot enter yet are NEW threads that were never started. "
                + "Emergency vehicles run at priority 10 and the database logger at 1.</html>");
        intro.setFont(Theme.BODY);
        top.add(intro, BorderLayout.NORTH);

        JPanel chips = new JPanel(new GridLayout(1, 0, 6, 0));
        for (Thread.State state : Thread.State.values()) {
            JLabel chip = new JLabel(state + ": 0", SwingConstants.CENTER);
            chip.setOpaque(true);
            chip.setBackground(Theme.stateColor(state));
            chip.setForeground(Color.WHITE);
            chip.setFont(Theme.HEADING);
            chip.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
            counters.put(state, chip);
            chips.add(chip);
        }
        top.add(chips, BorderLayout.CENTER);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        freeze.setToolTipText("Stop refreshing so you can read the table");
        options.add(freeze);
        total.setFont(Theme.BODY);
        options.add(total);
        top.add(options, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(560);
        table.getColumnModel().getColumn(3).setCellRenderer(new StateRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);

        new Timer(500, e -> refresh()).start();
    }

    private void refresh() {
        if (freeze.isSelected() || !isShowing()) {
            return;
        }
        SimulationEngine e = live.getEngine();
        List<ThreadRegistry.Row> rows = e == null ? new ArrayList<ThreadRegistry.Row>() : e.getRegistry().snapshot();
        model.setRows(rows);
        Map<Thread.State, Integer> counts = new EnumMap<Thread.State, Integer>(Thread.State.class);
        for (Thread.State s : Thread.State.values()) {
            counts.put(s, 0);
        }
        for (ThreadRegistry.Row r : rows) {
            counts.put(r.getState(), counts.get(r.getState()) + 1);
        }
        for (Map.Entry<Thread.State, Integer> c : counts.entrySet()) {
            counters.get(c.getKey()).setText(c.getKey() + ": " + c.getValue());
        }
        total.setText(e == null ? "No simulation is running. Start one on the Live Simulation tab."
                : rows.size() + " threads in this run (finished threads stay listed for a few seconds)");
    }

    /** Table model over a list of thread rows. */
    private static class ThreadTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Thread", "Role", "Priority", "State", "What it is doing"};
        private List<ThreadRegistry.Row> rows = new ArrayList<ThreadRegistry.Row>();

        void setRows(List<ThreadRegistry.Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 2 ? Integer.class : column == 3 ? Thread.State.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ThreadRegistry.Row r = rows.get(row);
            switch (column) {
                case 0:
                    return r.getName();
                case 1:
                    return r.getRole();
                case 2:
                    return r.getPriority();
                case 3:
                    return r.getState();
                default:
                    return r.getStatus();
            }
        }
    }

    /** Paints the State column in the colour of the state. */
    private static class StateRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            Thread.State state = (Thread.State) value;
            setHorizontalAlignment(CENTER);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!selected) {
                setBackground(Theme.stateColor(state));
                setForeground(Color.WHITE);
            }
            return this;
        }
    }
}
