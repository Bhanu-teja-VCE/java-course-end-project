package com.greencorridor.ui;

import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.exception.ScenarioFileException;
import com.greencorridor.io.ScenarioFiles;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Scenarios" tab: Create, Retrieve, Update and Delete scenarios.
 * A CardLayout switches between the list ("LIST" card) and the edit form ("FORM" card).
 */
public class ScenarioManagerPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String LIST = "LIST";
    private static final String FORM = "FORM";
    private static final String[] COLUMNS = {"ID", "Name", "Junctions", "Main road /min", "Cross street /min",
        "Signals", "Emergency priority", "Emergency every (s)", "Duration (s)"};

    private final AppContext ctx;
    private final MainFrame frame;
    private final CardLayout cards = new CardLayout();
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JLabel storageNote = new JLabel();
    private List<Scenario> scenarios;

    // ----- form fields -----
    private final JLabel formTitle = new JLabel();
    private final JTextField nameField = new JTextField(28);
    private final JTextField descriptionField = new JTextField(28);
    private final JComboBox<Integer> junctionBox = new JComboBox<Integer>(new Integer[]{1, 2, 3});
    private final JTextField mainRateField = new JTextField();
    private final JTextField crossRateField = new JTextField();
    private final JComboBox<SignalMode> signalBox = new JComboBox<SignalMode>(SignalMode.values());
    private final JComboBox<PriorityMode> priorityBox = new JComboBox<PriorityMode>(PriorityMode.values());
    private final JTextField mainGreenField = new JTextField();
    private final JTextField crossGreenField = new JTextField();
    private final JTextField yellowField = new JTextField();
    private final JTextField mixField = new JTextField();
    private final JTextField emergencyField = new JTextField();
    private final JTextField durationField = new JTextField();
    private final JTextField seedField = new JTextField();
    /** Field name used in InvalidScenarioException -> the component to focus. */
    private final Map<String, JComponent> fieldsByName = new HashMap<String, JComponent>();
    private Scenario editing;

    public ScenarioManagerPanel(AppContext ctx, MainFrame frame) {
        this.ctx = ctx;
        this.frame = frame;
        setLayout(cards);
        add(buildList(), LIST);
        add(buildForm(), FORM);
        cards.show(this, LIST);
    }

    // ----- list card -----

    private JPanel buildList() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Scenarios");
        title.setFont(Theme.TITLE);
        header.add(title, BorderLayout.NORTH);
        storageNote.setFont(Theme.BODY);
        storageNote.setForeground(Theme.MUTED);
        header.add(storageNote, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(280);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    editSelected();
                }
            }
        });
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(button("New", e -> openForm(new Scenario(), "New scenario")));
        buttons.add(button("Edit", e -> editSelected()));
        buttons.add(button("Duplicate", e -> duplicateSelected()));
        buttons.add(button("Delete", e -> deleteSelected()));
        JButton use = button("Use in simulation", e -> useSelected());
        use.setBackground(Theme.BRAND);
        use.setForeground(Color.WHITE);
        buttons.add(use);
        buttons.add(button("Export to file...", e -> exportSelected()));
        buttons.add(button("Import from file...", e -> importFromFile()));
        buttons.add(button("Refresh", e -> refresh()));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    /** Reloads the table from the repository (MySQL or memory). */
    public void refresh() {
        try {
            scenarios = ctx.getScenarios().findAll();
        } catch (RepositoryException e) {
            frame.showError("Could not load scenarios", e.getMessage());
            return;
        }
        tableModel.setRowCount(0);
        for (Scenario s : scenarios) {
            tableModel.addRow(new Object[]{s.getId(), s.getName(), s.getJunctionCount(), s.getMainRatePerMin(),
                s.getCrossRatePerMin(), s.getSignalMode().getLabel(), s.getPriorityMode().getLabel(),
                s.getEmergencyEverySec() == 0 ? "by hand" : s.getEmergencyEverySec(), s.getDurationSec()});
        }
        storageNote.setText(ctx.getScenarios().isPersistent()
                ? "Stored in the MySQL table \"scenario\". Double-click a row to edit it."
                : "Offline: changes last until the program closes. Use \"Export to file\" to keep a scenario.");
    }

    private Scenario selected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a scenario in the table first.", "No scenario selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        int id = (Integer) tableModel.getValueAt(table.convertRowIndexToModel(viewRow), 0);
        for (Scenario s : scenarios) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }

    /** Opens the editor for a table row (used by the screenshot tool). */
    void editRow(int row) {
        table.setRowSelectionInterval(row, row);
        editSelected();
    }

    private void editSelected() {
        Scenario s = selected();
        if (s != null) {
            openForm(s.copy(), "Edit scenario #" + s.getId());
        }
    }

    private void duplicateSelected() {
        Scenario s = selected();
        if (s != null) {
            Scenario copy = s.copy();
            copy.setId(0);
            copy.setName(s.getName() + " (copy)");
            openForm(copy, "New scenario (copy of #" + s.getId() + ")");
        }
    }

    private void deleteSelected() {
        Scenario s = selected();
        if (s == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Delete the scenario \"" + s.getName() + "\"?\n"
                + "Runs made with it are kept.", "Delete scenario", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ctx.getScenarios().delete(s.getId());
            frame.scenariosChanged();
        } catch (RepositoryException e) {
            frame.showError("Could not delete the scenario", e.getMessage());
        }
    }

    private void useSelected() {
        Scenario s = selected();
        if (s != null) {
            frame.useScenario(s.getId());
        }
    }

    // ----- file export / import (serialization) -----

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setFileFilter(new FileNameExtensionFilter("GreenCorridor scenario (*." + ScenarioFiles.EXTENSION + ")",
                ScenarioFiles.EXTENSION));
        return fc;
    }

    private void exportSelected() {
        Scenario s = selected();
        if (s == null) {
            return;
        }
        JFileChooser fc = chooser();
        fc.setSelectedFile(new File(s.getName().replaceAll("[^A-Za-z0-9 _-]", "") + "." + ScenarioFiles.EXTENSION));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        if (!file.getName().endsWith("." + ScenarioFiles.EXTENSION)) {
            file = new File(file.getParentFile(), file.getName() + "." + ScenarioFiles.EXTENSION);
        }
        try {
            ScenarioFiles.save(s, file);
            JOptionPane.showMessageDialog(this, "Saved to " + file.getAbsolutePath(), "Exported",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (ScenarioFileException e) {
            frame.showError("Export failed", e.getMessage());
        }
    }

    private void importFromFile() {
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Scenario s = ScenarioFiles.load(fc.getSelectedFile());
            openForm(s, "Import scenario from " + fc.getSelectedFile().getName());
        } catch (ScenarioFileException e) {
            frame.showError("Import failed", e.getMessage());
        }
    }

    // ----- form card -----

    private JPanel buildForm() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        formTitle.setFont(Theme.TITLE);
        outer.add(formTitle, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 6));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        addRow(grid, "Name", nameField, "name", "3 to 60 characters, must be unique");
        addRow(grid, "Description", descriptionField, "description", "Shown as a tooltip in the simulator");
        addRow(grid, "Number of junctions", junctionBox, "junctionCount", "1 to 3 signalled crossings");
        addRow(grid, "Main road traffic (vehicles/min per direction)", mainRateField, "mainRatePerMin", "0 to 40");
        addRow(grid, "Cross street traffic (vehicles/min per direction)", crossRateField, "crossRatePerMin", "0 to 30");
        addRow(grid, "Signal control", signalBox, "signalMode", "Fixed-time or vehicle-actuated");
        addRow(grid, "Emergency priority", priorityBox, "priorityMode", "None, local sensor or green corridor");
        addRow(grid, "Main road green (s)", mainGreenField, "mainGreenSec", "5 to 60");
        addRow(grid, "Cross street green (s)", crossGreenField, "crossGreenSec", "5 to 60");
        addRow(grid, "Yellow (s)", yellowField, "yellowSec", "2 to 6");
        addRow(grid, "Vehicle mix (TYPE:weight,...)", mixField, "vehicleMix", "Types: CAR, BIKE, AUTO, BUS, TRUCK");
        addRow(grid, "Emergency every (s, 0 = only by hand)", emergencyField, "emergencyEverySec",
                "0, or 10 to 600. Every third one is a fire engine");
        addRow(grid, "Duration (s)", durationField, "durationSec", "30 to 1800 simulated seconds");
        addRow(grid, "Random seed", seedField, "randomSeed", "Same seed = same pattern of arrivals");

        // FlowLayout keeps the grid at its preferred width instead of stretching it
        JPanel gridHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gridHolder.add(grid);
        JPanel center = new JPanel(new BorderLayout());
        center.add(gridHolder, BorderLayout.NORTH);
        JLabel hint = new JLabel("<html>Hover over a field for its allowed range. Enter saves, Esc cancels. "
                + "The form is checked by Scenario.validate(), which throws InvalidScenarioException naming the "
                + "wrong field.</html>");
        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.MUTED);
        hint.setVerticalAlignment(JLabel.TOP);
        center.add(hint, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(center);
        scroll.setBorder(null);
        outer.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton save = button("Save", e -> saveForm());
        save.setBackground(Theme.BRAND);
        save.setForeground(Color.WHITE);
        buttons.add(save);
        buttons.add(button("Cancel", e -> cards.show(this, LIST)));
        outer.add(buttons, BorderLayout.SOUTH);

        // keyboard: Enter saves, Escape cancels (key bindings on the form)
        outer.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "save");
        outer.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        outer.getActionMap().put("save", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                saveForm();
            }
        });
        outer.getActionMap().put("cancel", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                cards.show(ScenarioManagerPanel.this, LIST);
            }
        });
        return outer;
    }

    private void addRow(JPanel grid, String label, JComponent field, String fieldName, String tip) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.BODY);
        l.setToolTipText(tip);
        field.setToolTipText(tip);
        grid.add(l);
        grid.add(field);
        fieldsByName.put(fieldName, field);
    }

    private void openForm(Scenario s, String title) {
        editing = s;
        formTitle.setText(title);
        nameField.setText(s.getName());
        descriptionField.setText(s.getDescription());
        junctionBox.setSelectedItem(s.getJunctionCount());
        mainRateField.setText(String.valueOf(s.getMainRatePerMin()));
        crossRateField.setText(String.valueOf(s.getCrossRatePerMin()));
        signalBox.setSelectedItem(s.getSignalMode());
        priorityBox.setSelectedItem(s.getPriorityMode());
        mainGreenField.setText(String.valueOf(s.getMainGreenSec()));
        crossGreenField.setText(String.valueOf(s.getCrossGreenSec()));
        yellowField.setText(String.valueOf(s.getYellowSec()));
        mixField.setText(s.getVehicleMix());
        emergencyField.setText(String.valueOf(s.getEmergencyEverySec()));
        durationField.setText(String.valueOf(s.getDurationSec()));
        seedField.setText(String.valueOf(s.getRandomSeed()));
        cards.show(this, FORM);
        nameField.requestFocusInWindow();
    }

    private void saveForm() {
        Scenario s = editing.copy();
        try {
            s.setName(nameField.getText());
            s.setDescription(descriptionField.getText());
            s.setJunctionCount((Integer) junctionBox.getSelectedItem());
            s.setMainRatePerMin(number(mainRateField, "mainRatePerMin", "Main road traffic"));
            s.setCrossRatePerMin(number(crossRateField, "crossRatePerMin", "Cross street traffic"));
            s.setSignalMode((SignalMode) signalBox.getSelectedItem());
            s.setPriorityMode((PriorityMode) priorityBox.getSelectedItem());
            s.setMainGreenSec(number(mainGreenField, "mainGreenSec", "Main road green"));
            s.setCrossGreenSec(number(crossGreenField, "crossGreenSec", "Cross street green"));
            s.setYellowSec(number(yellowField, "yellowSec", "Yellow"));
            s.setVehicleMix(mixField.getText());
            s.setEmergencyEverySec(number(emergencyField, "emergencyEverySec", "Emergency interval"));
            s.setDurationSec(number(durationField, "durationSec", "Duration"));
            s.setRandomSeed(number(seedField, "randomSeed", "Random seed"));
            Scenario saved = ctx.getScenarios().save(s);       // validates, then INSERT or UPDATE
            cards.show(this, LIST);
            frame.scenariosChanged();
            selectRow(saved.getId());
        } catch (InvalidScenarioException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Please correct this field", JOptionPane.WARNING_MESSAGE);
            JComponent field = fieldsByName.get(e.getField());
            if (field != null) {
                field.requestFocusInWindow();
            }
        } catch (RepositoryException e) {
            frame.showError("Could not save the scenario", e.getMessage());
        }
    }

    /** Reads a whole number from a text field, turning NumberFormatException into a friendly error. */
    private static int number(JTextField field, String name, String label) throws InvalidScenarioException {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new InvalidScenarioException(name, label + " must be a whole number.");
        }
    }

    private void selectRow(int id) {
        for (int r = 0; r < table.getRowCount(); r++) {
            if (((Integer) table.getValueAt(r, 0)) == id) {
                table.setRowSelectionInterval(r, r);
                table.scrollRectToVisible(table.getCellRect(r, 0, true));
                return;
            }
        }
    }
}
