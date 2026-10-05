package com.greencorridor.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;

/** A small card with a caption and one big number. */
public class StatTile extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel value = new JLabel("-");

    public StatTile(String caption, String tooltip) {
        super(new BorderLayout(0, 2));
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JLabel title = new JLabel(caption);
        title.setFont(Theme.SMALL);
        title.setForeground(Theme.MUTED);
        value.setFont(Theme.VALUE);
        value.setForeground(Theme.TEXT);
        add(title, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
        setToolTipText(tooltip);
    }

    public void setValue(String text) {
        value.setText(text);
    }

    public void setValue(String text, Color color) {
        value.setText(text);
        value.setForeground(color);
    }
}
