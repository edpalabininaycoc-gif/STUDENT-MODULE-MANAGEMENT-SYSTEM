package moduledistributionsystem.ui.common;

import javax.swing.*;
import java.awt.*;
import moduledistributionsystem.data.DemoDataStore;

/**
 * The login window: a plain frame that holds one LoginPanel. When a login succeeds, LoginPanel closes
 * it and opens the Student, Staff or Admin main window.
 */
public class LoginFrame extends JFrame {
    /** The shared data store. */
    private final DemoDataStore store;

    /** Creates the maximized login window. */
    public LoginFrame(DemoDataStore store) {
        this.store = store;
        setTitle("Student Module Management System \u2014 Sign in");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // subtle header bar
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(245, 247, 250));
        header.setPreferredSize(new Dimension(0, 56));
        

        // center the login panel
        add(new LoginPanel(store, this), BorderLayout.CENTER);
    }
}
