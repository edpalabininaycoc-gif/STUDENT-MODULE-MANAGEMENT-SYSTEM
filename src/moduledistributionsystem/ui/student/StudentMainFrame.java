package moduledistributionsystem.ui.student;

import moduledistributionsystem.ui.common.LoginFrame;
import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Student;

/**
 * The student portal window: a green sidebar (Overview, Modules, Appointments, Notifications, Student
 * Queries) and the page of the selected section on the right. Opened by LoginPanel after a student
 * signs in.
 *
 * How it works: the five pages are created once and stacked in a CardLayout; clicking a sidebar button
 * shows one of them (showSection). Each time the section changes, the data is re-read from the database
 * and the signed-in account is re-checked (a deactivated account is signed out).
 *
 * How to customize: add a page by creating the panel class, adding its name to MENU_LABELS /
 * MENU_GLYPHS, and adding it to the card layout in the constructor and in showSection.
 */
public class StudentMainFrame extends JFrame {
    /** The shared data store. */
    private DemoDataStore store;
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    /** Page 0: dashboard with the "At a glance" cards. */
    private OverviewPanel overviewPanel;
    /** Page 1: the student's modules and booking. */
    private ModulesPanel modulesPanel;
    /** Page 2: upcoming appointments, booking / rescheduling and history. */
    private AppointmentsPanel appointmentsPanel;
    /** Page 3: notifications. */
    private NotificationsPanel notificationsPanel;
    /** Page 4: student queries. */
    private StudentQueriesPanel queriesPanel;

    /** Text of the sidebar buttons, in page order. */
    private static final String[] MENU_LABELS = {"Overview", "Modules", "Appointments", "Notifications", "Student Queries"};
    /** Icon (symbol) of each sidebar button. */
    private static final String[] MENU_GLYPHS = {"\u25A6", "\uD83D\uDCD8", "\uD83D\uDCC5", "\uD83D\uDD14", "\uD83D\uDCAC"};
    /** The sidebar buttons, so the active one can be highlighted. */
    private SidebarButton[] sidebarButtons;

    /** Builds the window: sidebar, the five pages and the first page shown. */
    public StudentMainFrame(DemoDataStore store) {
        this.store = store;
        setTitle("Student Portal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);

        overviewPanel = new OverviewPanel(store, this);
        modulesPanel = new ModulesPanel(store, this);
        appointmentsPanel = new AppointmentsPanel(store, this);
        notificationsPanel = new NotificationsPanel(store, this);
        queriesPanel = new StudentQueriesPanel(store, this);

        mainPanel.setBackground(Color.WHITE);
        mainPanel.add(overviewPanel, "Overview");
        mainPanel.add(modulesPanel, "Modules");
        mainPanel.add(appointmentsPanel, "Appointments");
        mainPanel.add(notificationsPanel, "Notifications");
        mainPanel.add(queriesPanel, "Queries");

        add(mainPanel, BorderLayout.CENTER);
        showSection(0);
    }

    /** Builds the left sidebar: logo, menu buttons and the profile card. */
    private JPanel buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setPreferredSize(new Dimension(250, 0));
        side.setBackground(UIKit.SAGE);
        side.setBorder(new EmptyBorder(24, 18, 18, 18));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        logoRow.setOpaque(false);
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        UIKit.LogoBadge logoIcon = new UIKit.LogoBadge(40);
        JPanel logoText = new JPanel();
        logoText.setLayout(new BoxLayout(logoText, BoxLayout.Y_AXIS));
        logoText.setOpaque(false);
        JLabel portal = new JLabel("Portal");
        portal.setFont(new Font("SansSerif", Font.BOLD, 17));
        portal.setForeground(UIKit.TEXT_DARK);
        JLabel gateway = new JLabel("STUDENT GATEWAY");
        gateway.setFont(new Font("SansSerif", Font.BOLD, 9));
        gateway.setForeground(new Color(55, 78, 58));
        logoText.add(portal);
        logoText.add(gateway);
        logoRow.add(logoIcon);
        logoRow.add(logoText);
        top.add(logoRow);
        top.add(Box.createVerticalStrut(28));

        JLabel menuLabel = new JLabel("MENU");
        menuLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        menuLabel.setForeground(new Color(55, 78, 58));
        menuLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        menuLabel.setBorder(new EmptyBorder(0, 4, 10, 0));
        top.add(menuLabel);

        sidebarButtons = new SidebarButton[MENU_LABELS.length];
        for (int i = 0; i < MENU_LABELS.length; i++) {
            int idx = i;
            SidebarButton btn = new SidebarButton(MENU_GLYPHS[i], MENU_LABELS[i]);
            btn.addActionListener(e -> showSection(idx));
            btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebarButtons[i] = btn;
            top.add(btn);
            top.add(Box.createVerticalStrut(6));
        }

        side.add(top, BorderLayout.NORTH);
        side.add(buildProfileCard(), BorderLayout.SOUTH);
        return side;
    }

    /**
     * Builds the card at the bottom of the sidebar with the student's name, School ID and logout
     * button.
     */
    private JPanel buildProfileCard() {
        Student s = store.getCurrentStudent();
        UIKit.RoundPanel card = new UIKit.RoundPanel(14, UIKit.SAGE_DARK);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setPreferredSize(new Dimension(0, 62));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JPanel avatar = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(75, 165, 115));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(36, 36));
        JLabel avLabel = new JLabel(initialsOf(s.getName()));
        avLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        avLabel.setForeground(Color.WHITE);
        avatar.add(avLabel);

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);
        JLabel name = new JLabel(firstName(s.getName()));
        name.setFont(new Font("SansSerif", Font.BOLD, 13));
        name.setForeground(Color.WHITE);
        JLabel id = new JLabel(s.getId());
        id.setFont(new Font("SansSerif", Font.PLAIN, 10));
        id.setForeground(new Color(225, 235, 227));
        textCol.add(name);
        textCol.add(id);

        left.add(avatar);
        left.add(textCol);

        JButton logout = new JButton("\u21B7");
        logout.setFont(new Font("SansSerif", Font.BOLD, 16));
        logout.setForeground(Color.WHITE);
        logout.setBorderPainted(false);
        logout.setContentAreaFilled(false);
        logout.setFocusPainted(false);
        logout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logout.setToolTipText("Log out");
        logout.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this, "Do you want to log out?", "Log Out", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                store.logout();
                dispose();
                SwingUtilities.invokeLater(() -> new LoginFrame(store).setVisible(true));
            }
        });

        card.add(left, BorderLayout.WEST);
        card.add(logout, BorderLayout.EAST);
        return card;
    }

    /** First letters of the name, shown inside the avatar circle. */
    private String initialsOf(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.toString();
    }

    /** The first word of a name. */
    private String firstName(String name) {
        String[] parts = name.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : name;
    }

    /**
     * Switches to a section (0 = Overview ... 4 = Student Queries): re-reads the data, signs the user
     * out if the account was deactivated meanwhile, highlights the button and shows the page. Public so
     * the Overview cards can jump to a page.
     */
    public void showSection(int idx) {
        store.reloadFromDatabase(); // no-op unless a database is connected: pick up other computers' changes
        if (!store.isSessionStillValid(moduledistributionsystem.model.Role.STUDENT)) {
            JOptionPane.showMessageDialog(this, "Your account was deactivated, archived or removed. You have been signed out.",
                    "Signed out", JOptionPane.WARNING_MESSAGE);
            store.logout();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame(store).setVisible(true));
            return;
        }
        for (int i = 0; i < sidebarButtons.length; i++) sidebarButtons[i].setActive(i == idx);
        switch (idx) {
            case 0: overviewPanel.refresh(); cardLayout.show(mainPanel, "Overview"); break;
            case 1: modulesPanel.refresh(); cardLayout.show(mainPanel, "Modules"); break;
            case 2: appointmentsPanel.refresh(); cardLayout.show(mainPanel, "Appointments"); break;
            case 3: notificationsPanel.refresh(); cardLayout.show(mainPanel, "Notifications"); break;
            case 4: queriesPanel.refresh(); cardLayout.show(mainPanel, "Queries"); break;
            default: break;
        }
    }

    /** Jump to the Appointments section (used by the "Booked" button on the Modules page). */
    public void showAppointments() { showSection(2); }

    /** Refreshes every page (called after a change that other pages also show). */
    public void refreshAll() {
        overviewPanel.refresh();
        modulesPanel.refresh();
        appointmentsPanel.refresh();
        notificationsPanel.refresh();
        queriesPanel.refresh();
    }

    /** A sidebar menu button that draws a rounded highlight when it is the active section. */
    static class SidebarButton extends JButton {
        /** True for the button of the page being shown. */
        private boolean active = false;

        SidebarButton(String glyph, String text) {
            super("  " + glyph + "   " + text);
            setHorizontalAlignment(SwingConstants.LEFT);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(new Font("SansSerif", Font.PLAIN, 13));
            setForeground(UIKit.TEXT_DARK);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setPreferredSize(new Dimension(214, 40));
        }

        /** Highlights (or un-highlights) the button. */
        void setActive(boolean a) {
            active = a;
            setFont(getFont().deriveFont(a ? Font.BOLD : Font.PLAIN));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (active) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIKit.SAGE_DARK);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
