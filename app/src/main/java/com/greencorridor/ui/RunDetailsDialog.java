package com.greencorridor.ui;

import com.greencorridor.db.RunDao;
import com.greencorridor.exception.RepositoryException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/** Per-vehicle-type figures and the list of pre-emptions of one stored run. */
public class RunDetailsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    public RunDetailsDialog(JFrame owner, RunDao dao, int runId, String title) throws RepositoryException {
        super(owner, "Run #" + runId + " - " + title, true);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("By vehicle type", new JScrollPane(table(dao.typeBreakdown(runId))));
        tabs.addTab("Signal pre-emptions", new JScrollPane(table(dao.preemptions(runId))));
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(tabs, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(new Dimension(760, 380));
        setLocationRelativeTo(owner);
    }

    /** First row of "rows" is the header (column names from ResultSetMetaData). */
    private static JTable table(List<Object[]> rows) {
        Object[] header = rows.get(0);
        DefaultTableModel model = new DefaultTableModel(header, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (int i = 1; i < rows.size(); i++) {
            model.addRow(rows.get(i));
        }
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.setAutoCreateRowSorter(true);
        return table;
    }
}
