package com.greencorridor.ui;

import com.greencorridor.db.Database;
import com.greencorridor.db.DbConfig;
import com.greencorridor.exception.DatabaseUnavailableException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/** Lets each lab machine enter its own MySQL host, user and password (saved to db.properties). */
public class DbSettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final MainFrame frame;
    private final JTextField hostField = new JTextField(18);
    private final JTextField portField = new JTextField(6);
    private final JTextField databaseField = new JTextField(18);
    private final JTextField userField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel result = new JLabel(" ");

    public DbSettingsDialog(MainFrame frame) {
        super(frame, "MySQL connection settings", true);
        this.frame = frame;
        DbConfig c = Database.getConfig();
        hostField.setText(c.getHost());
        portField.setText(String.valueOf(c.getPort()));
        databaseField.setText(c.getDatabase());
        userField.setText(c.getUser());
        passwordField.setText(c.getPassword());

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
        grid.add(new JLabel("Host"));
        grid.add(hostField);
        grid.add(new JLabel("Port"));
        grid.add(portField);
        grid.add(new JLabel("Database (created if missing)"));
        grid.add(databaseField);
        grid.add(new JLabel("User"));
        grid.add(userField);
        grid.add(new JLabel("Password"));
        grid.add(passwordField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton test = new JButton("Test connection");
        JButton save = new JButton("Save and connect");
        JButton cancel = new JButton("Cancel");
        buttons.add(test);
        buttons.add(save);
        buttons.add(cancel);
        test.addActionListener(e -> testConnection());
        save.addActionListener(e -> saveAndConnect());
        cancel.addActionListener(e -> dispose());

        result.setFont(Theme.BODY);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel note = new JLabel("<html>Settings are stored in <b>db.properties</b> next to the program.</html>");
        note.setFont(Theme.SMALL);
        content.add(note, BorderLayout.NORTH);
        content.add(grid, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(result, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(frame);
    }

    private DbConfig readForm() throws NumberFormatException {
        DbConfig c = new DbConfig();
        c.setHost(hostField.getText().trim());
        c.setPort(Integer.parseInt(portField.getText().trim()));
        c.setDatabase(databaseField.getText().trim());
        c.setUser(userField.getText().trim());
        c.setPassword(new String(passwordField.getPassword()));
        return c;
    }

    private void testConnection() {
        final DbConfig c;
        try {
            c = readForm();
        } catch (NumberFormatException e) {
            showResult("Port must be a number.", false);
            return;
        }
        showResult("Connecting...", true);
        // Never block the event dispatch thread with network work.
        new Thread(() -> {
            String message;
            boolean ok;
            try (Connection con = Database.getConnection(c)) {
                DatabaseMetaData meta = con.getMetaData();
                message = "Connected: " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion()
                        + " via " + meta.getDriverName() + " " + meta.getDriverVersion();
                ok = true;
            } catch (DatabaseUnavailableException | SQLException e) {
                message = e.getMessage();
                ok = false;
            }
            final String m = message;
            final boolean success = ok;
            SwingUtilities.invokeLater(() -> showResult(m, success));
        }, "DB-Test").start();
    }

    private void saveAndConnect() {
        DbConfig c;
        try {
            c = readForm();
        } catch (NumberFormatException e) {
            showResult("Port must be a number.", false);
            return;
        }
        try {
            c.save();
        } catch (IOException e) {
            showResult("Could not write " + DbConfig.FILE_NAME + ": " + e.getMessage(), false);
            return;
        }
        Database.setConfig(c);
        dispose();
        frame.connectToDatabase(true);
    }

    private void showResult(String text, boolean ok) {
        result.setForeground(ok ? Theme.BRAND : Theme.DANGER);
        result.setText("<html><div style='width:380px'>" + MainFrame.html(text) + "</div></html>");
        pack();
    }
}
