package com.greencorridor;

import com.greencorridor.ui.MainFrame;
import com.greencorridor.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * GreenCorridor - Smart Traffic Junction Simulator with an emergency vehicle green corridor.
 * Starts the Swing user interface on the event dispatch thread.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    UIManager.put("nimbusBase", Theme.BRAND_DARK);
                    UIManager.put("nimbusSelectionBackground", Theme.BRAND);
                    UIManager.put("control", Theme.PANEL);
                    break;
                }
            }
        } catch (Exception e) {
            // keep the default look and feel
        }
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.connectToDatabase(false);
        });
    }
}
