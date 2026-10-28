package moduledistributionsystem.ui.admin;

import moduledistributionsystem.ui.common.LoginFrame;
import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import moduledistributionsystem.model.Admin;
import moduledistributionsystem.data.DemoDataStore;

/**
 * The administrator portal window: a sidebar (Manage Users, Appointments, Reports, System Activity) and the page
 * of the selected section on the right. Opened by LoginPanel after an administrator signs in. It works
 * exactly like StaffMainFrame and StudentMainFrame (see their notes).
 */
public class AdminMainFrame extends JFrame {
    /** The shared data store. */
    private DemoDataStore store;
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    /** Page 1: user accounts. */
    private ManageUsersPanel usersPanel;
    /** Page 2: all appointments and escalated queries. */
    private AppointmentsOverridePanel appointmentsPanel;
    /** Page 4: the system activity log. */
    private SystemActivityPanel activityPanel;

    /** Page 3: summary reports (JOIN / GROUP BY), CSV export and print. */
    private ReportsPanel reportsPanel;

    /** Text of the sidebar buttons, in page order. */
    private static final String[] MENU_LABELS = {"Manage Users", "Appointments", "Reports", "System Activity"};
    /** Icon (emoji) of each sidebar button. */
    private static final String[] MENU_GLYPHS = {"\uD83D\uDC65", "\uD83D\uDCC5", "\uD83D\uDCCA", "\uD83D\uDCC8"};
    /** The sidebar buttons, so the active one can be highlighted. */
    private SidebarButton[] sidebarButtons;

    /** Builds the window: sidebar, the three pages and the first page shown. */
    public AdminMainFrame(DemoDataStore store) {
        this.store = store;
        setTitle("Admin Portal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);

        usersPanel = new ManageUsersPanel(store, this);
        appointmentsPanel = new AppointmentsOverridePanel(store, this);
        reportsPanel = new ReportsPanel(store, this);
        activityPanel = new SystemActivityPanel(store, this);

        mainPanel.setBackground(Color.WHITE);
        mainPanel.add(usersPanel, "Users");
        mainPanel.add(appointmentsPanel, "Appointments");
        mainPanel.add(reportsPanel, "Reports");
        mainPanel.add(activityPanel, "Activity");

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
        JLabel logoIcon = new JLabel("\uD83C\uDF93");
        logoIcon.setFont(new Font("SansSerif", Font.PLAIN, 22));
        JPanel logoText = new JPanel();
        logoText.setLayout(new BoxLayout(logoText, BoxLayout.Y_AXIS));
        logoText.setOpaque(false);
        JLabel portal = new JLabel("Portal");
        portal.setFont(new Font("SansSerif", Font.BOLD, 17));
        portal.setForeground(UIKit.TEXT_DARK);
        JLabel gateway = new JLabel("ADMIN GATEWAY");
        gateway.setFont(new Font("SansSerif", Font.BOLD, 9));
        gateway.setForeground(new Color(55, 78, 58));
        logoText.add(portal);
        logoText.add(gateway);
        logoRow.add(logoIcon);
        logoRow.add(logoText);
        top.add(logoRow);
        top.add(Box.createVerticalStrut(28));

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
     * Builds the card at the bottom of the sidebar with the administrator's name, role and logout
     * button.
     */
    private JPanel buildProfileCard() {
        Admin ad = store.getCurrentAdmin();
        String name = ad != null ? firstName(ad.getName()) : "Admin";
        String title = ad != null ? ad.getTitle() : "";

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
        JLabel avLabel = new JLabel(initialsOf(ad != null ? ad.getName() : "Admin"));
        avLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        avLabel.setForeground(Color.WHITE);
        avatar.add(avLabel);

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        nameLabel.setForeground(Color.WHITE);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        titleLabel.setForeground(new Color(225, 235, 227));
        textCol.add(nameLabel);
        textCol.add(titleLabel);

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
     * Switches to a section: re-reads the data, signs the user out if the account was deactivated
     * meanwhile, highlights the button and shows the page.
     */
    private void showSection(int idx) {
        store.reloadFromDatabase(); // no-op unless a database is connected: pick up other computers' changes
        if (!store.isSessionStillValid(moduledistributionsystem.model.Role.ADMIN)) {
            JOptionPane.showMessageDialog(this, "Your account was deactivated, archived or removed. You have been signed out.",
                    "Signed out", JOptionPane.WARNING_MESSAGE);
            store.logout();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame(store).setVisible(true));
            return;
        }
        for (int i = 0; i < sidebarButtons.length; i++) sidebarButtons[i].setActive(i == idx);
        switch (idx) {
            case 0: usersPanel.refresh(); cardLayout.show(mainPanel, "Users"); break;
            case 1: appointmentsPanel.refresh(); cardLayout.show(mainPanel, "Appointments"); break;
            case 2: reportsPanel.refresh(); cardLayout.show(mainPanel, "Reports"); break;
            case 3: activityPanel.refresh(); cardLayout.show(mainPanel, "Activity"); break;
            default: break;
        }
    }

    /** The shared page header with this admin's name. */
    static JComponent header(DemoDataStore store, String title, String sub, String crumb) {
        Admin ad = store.getCurrentAdmin();
        return moduledistributionsystem.ui.staff.InventoryPanel.buildHeader(title, sub, crumb,
                ad != null ? ad.getName() : "Admin", ad != null ? ad.getTitle() : "");
    }

    /** Refreshes every page. */
    public void refreshAll() {
        usersPanel.refresh();
        appointmentsPanel.refresh();
        reportsPanel.refresh();
        activityPanel.refresh();
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
