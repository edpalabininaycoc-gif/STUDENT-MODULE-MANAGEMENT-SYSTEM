package moduledistributionsystem.ui.admin;

import moduledistributionsystem.ui.common.UIKit;
import moduledistributionsystem.ui.staff.InventoryPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import moduledistributionsystem.model.Account;
import moduledistributionsystem.model.Admin;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.LogStatus;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.model.Staff;
import moduledistributionsystem.model.Student;
import moduledistributionsystem.model.YearLevel;

/**
 * Admin page "Manage Users": one table of every student, staff and administrator account, with search,
 * Role and Status filters (All / Active / Inactive / Archived), the Current Term chooser and "+ Add
 * Account".
 *
 * Each row has a "Manage" menu: Change password, Enrollment (students), Activate / Deactivate, Archive
 * / Restore. Only a DEACTIVATED account can be archived; an archived account can not sign in. Accounts
 * can not be deleted from here (delete is not allowed).
 *
 * The rules (you can not lock yourself or the last admin out, e-mails must be unique...) live in
 * DemoDataStore; this class only shows the screens.
 */
public class ManageUsersPanel extends JPanel {
    /** Column titles of the accounts table. */
    private static final String[] LABELS = {"NAME", "SCHOOL ID / EMAIL", "ROLE", "STATUS", "YEAR", "MODULES", "ACTIONS"};
    /** Minimum width of each column. */
    private static final int[] MINW   = {150, 200, 76, 84, 70, 72, 100};
    /** How the free width is shared between the columns. */
    private static final int[] WEIGHT = {6, 8, 2, 3, 2, 2, 3};
    private static final int[] ALIGN  = {InventoryPanel.LEFT, InventoryPanel.LEFT, InventoryPanel.CENTER, InventoryPanel.CENTER,
            InventoryPanel.CENTER, InventoryPanel.CENTER, InventoryPanel.RIGHT};

    /** Names of the Status filter choices. "All" means every account that is not archived. */
    private static final String S_ALL = "All", S_ACTIVE = "Active", S_INACTIVE = "Inactive", S_ARCHIVED = "Archived";

    /** The shared data store. */
    private DemoDataStore store;
    /** The admin window. */
    private AdminMainFrame parent;
    /** The shared page skeleton (header, stat cards, gray card). */
    private final InventoryPanel.Page page;
    private final JPanel tableBody = new JPanel();
    /** Search by name, School ID or e-mail. */
    private InventoryPanel.SearchBox searchBox;
    /** Filter by role. */
    private InventoryPanel.Dropdown roleFilter;
    /** Filter by Active / Inactive / Archived. */
    private InventoryPanel.Dropdown statusFilter;

    private static final Color PURPLE_BG = new Color(243, 232, 255);
    private static final Color PURPLE_FG = new Color(147, 51, 234);

    /** Builds the page: header, stat cards, toolbar (search, filters, term, add) and the table. */
    public ManageUsersPanel(DemoDataStore store, AdminMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(InventoryPanel.PAGE_BG);

        page = new InventoryPanel.Page(AdminMainFrame.header(store, "Manage Users",
                "Add, deactivate, archive, and manage student and staff accounts", "User Administration"), 4);
        add(page.root, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 15, 0));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setOpaque(false);
        searchBox = new InventoryPanel.SearchBox("Search accounts...", 220);
        roleFilter = new InventoryPanel.Dropdown("Role: ");
        Set<String> roles = new LinkedHashSet<>();
        roles.add("All");
        roles.add("Student");
        roles.add("Staff");
        roles.add("Admin");
        roleFilter.setItems(roles);
        statusFilter = new InventoryPanel.Dropdown("Status: ");
        Set<String> sts = new LinkedHashSet<>();
        sts.add(S_ALL);
        sts.add(S_ACTIVE);
        sts.add(S_INACTIVE);
        sts.add(S_ARCHIVED);
        statusFilter.setItems(sts);
        statusFilter.setToolTipText("All = every account that is not archived");
        filters.add(searchBox);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(roleFilter);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(statusFilter);
        toolbar.add(filters, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JLabel termLabel = new JLabel("Current Term:");
        termLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        termLabel.setForeground(InventoryPanel.SLATE);
        termLabel.setToolTipText("The semester being distributed right now. Students only see modules for this term.");
        TermButton termBtn = new TermButton(store.getCurrentSemester());
        termBtn.setToolTipText("The semester being distributed right now. Students only see modules for this term.");
        termBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            for (String sem : new String[]{"Semester 1", "Semester 2"}) {
                JMenuItem mi = new JMenuItem(sem);
                mi.setFont(new Font("SansSerif", sem.equals(store.getCurrentSemester()) ? Font.BOLD : Font.PLAIN, 13));
                mi.addActionListener(ev -> {
                    if (sem.equals(store.getCurrentSemester())) return;
                    int ok = JOptionPane.showConfirmDialog(this,
                            "Switch the current term to " + sem + "?\n\nEvery student will now see the " + sem
                                    + " modules for their year level.",
                            "Change Current Term", JOptionPane.YES_NO_OPTION);
                    if (ok != JOptionPane.YES_OPTION) return;
                    store.setCurrentSemester(sem);
                    termBtn.setText(sem);
                    store.addLog(adminLabel(), "Changed current term to " + sem, LogStatus.SUCCESS);
                    if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
                    refresh();
                });
                menu.add(mi);
            }
            menu.show(termBtn, 0, termBtn.getHeight() + 2);
        });
        UIKit.RoundButton addBtn = InventoryPanel.grayButton("+ Add Account");
        addBtn.addActionListener(e -> openAddAccount());
        right.add(termLabel);
        right.add(termBtn);
        right.add(addBtn);
        toolbar.add(right, BorderLayout.EAST);
        page.card.add(toolbar, BorderLayout.NORTH);

        tableBody.setLayout(new BoxLayout(tableBody, BoxLayout.Y_AXIS));
        tableBody.setOpaque(false);
        page.card.add(tableBody, BorderLayout.CENTER);

        searchBox.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        roleFilter.setOnChange(this::refresh);
        statusFilter.setOnChange(this::refresh);
        refresh();
    }

    /** "Name (administrator)" text used in the log lines. */
    private String adminLabel() {
        return (store.getCurrentAdmin() != null ? store.getCurrentAdmin().getName() : "Admin") + " (administrator)";
    }

    // ---- Helpers: every account is an Account (Student / Staff / Admin), so the base-class methods are used directly ----
    /** Students: how many modules they can see this term (same rule as their Modules page). Staff/Admin: -1 = dash. */
    private int modulesCountOf(Account a) {
        if (a instanceof Student) return store.getCurrentModulesFor((Student) a).size();
        return -1;
    }

    /**
     * Every account of every role in one list (this works because Student, Staff and Admin all extend
     * Account).
     */
    private List<Account> allAccounts() {
        List<Account> list = new ArrayList<>();
        list.addAll(store.getStudentAccounts());
        list.addAll(store.getStaffAccounts());
        list.addAll(store.getAdminAccounts());
        return list;
    }

    /** The colored Student / Staff / Admin badge. */
    private static InventoryPanel.StatusBadge roleBadge(String role) {
        switch (role) {
            case "Staff": return new InventoryPanel.StatusBadge("Staff", PURPLE_BG, PURPLE_FG, PURPLE_FG);
            case "Admin": return new InventoryPanel.StatusBadge("Admin", new Color(230, 225, 255), new Color(88, 28, 235), new Color(88, 28, 235));
            default: return new InventoryPanel.StatusBadge("Student", UIKit.BLUE_PILL_BG, UIKit.BLUE_PILL_FG, new Color(59, 130, 246));
        }
    }

    /** The colored Active / Inactive / Archived badge. */
    private static InventoryPanel.StatusBadge statusBadge(boolean active, boolean archived) {
        if (archived) return new InventoryPanel.StatusBadge("Archived", new Color(235, 235, 235), new Color(80, 80, 80), new Color(140, 140, 140));
        return active
                ? new InventoryPanel.StatusBadge("Active", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129))
                : new InventoryPanel.StatusBadge("Inactive", new Color(254, 226, 226), new Color(153, 27, 27), new Color(239, 68, 68));
    }

    /** Refreshes this page and the other pages after a change. */
    private void changed() {
        if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
        refresh();
    }

    /** Shows the store's "not allowed" message and returns true when a reason is given. */
    private boolean refused(String reason) {
        if (reason == null) return false;
        JOptionPane.showMessageDialog(this, reason, "Not allowed", JOptionPane.WARNING_MESSAGE);
        return true;
    }

    /** Builds one table row for an account. */
    private JComponent buildRow(Account acct) {
        InventoryPanel.RowPanel row = new InventoryPanel.RowPanel(InventoryPanel.ROW_H, true, MINW, WEIGHT, ALIGN, 1);
        boolean archived = acct.isArchived();
        row.add(InventoryPanel.cellLabel(acct.getName(), true, archived ? InventoryPanel.SLATE : InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(acct.getLoginId(), false, InventoryPanel.SLATE));
        row.add(roleBadge(acct.getRoleName()));
        row.add(statusBadge(acct.isActive(), archived));
        String yearText = (acct instanceof Student && ((Student) acct).getYearLevel() != null) ? ((Student) acct).getYearLevel().toString() : "\u2014";
        row.add(InventoryPanel.cellLabel(yearText, false, InventoryPanel.INK));
        int modN = modulesCountOf(acct);
        row.add(InventoryPanel.cellLabel(modN < 0 ? "\u2014" : String.valueOf(modN), false, InventoryPanel.INK));

        UIKit.RoundButton manage = InventoryPanel.rowButton("Manage \u25BE", true);
        manage.addActionListener(e -> showManageMenu(manage, acct));
        row.add(manage);
        return row;
    }

    /**
     * Opens the Manage menu of a row: Change password, Enrollment, Activate / Deactivate, Archive /
     * Restore.
     */
    private void showManageMenu(Component anchor, Account acct) {
        JPopupMenu menu = new JPopupMenu();
        boolean archived = acct.isArchived(), active = acct.isActive();
        if (!archived) {
            JMenuItem pw = new JMenuItem("Change password");
            pw.addActionListener(e -> openResetPassword(acct));
            menu.add(pw);
            if (acct instanceof Student) {
                JMenuItem en = new JMenuItem("Enrollment");
                en.addActionListener(e -> openEnrollmentDialog((Student) acct));
                menu.add(en);
            }
            menu.addSeparator();
        }
        if (active && !archived) {
            JMenuItem off = new JMenuItem("Deactivate");
            off.addActionListener(e -> {
                if (refused(store.setAccountActive(acct, false))) return;
                store.addLog(adminLabel(), "Account Deactivated: " + acct.getName() + " (" + acct.getRoleName() + ")", LogStatus.SUCCESS);
                changed();
            });
            menu.add(off);
        } else {
            JMenuItem on = new JMenuItem(archived ? "Restore & activate" : "Activate");
            on.addActionListener(e -> {
                store.setAccountActive(acct, true);
                store.addLog(adminLabel(), (archived ? "Account Restored: " : "Account Activated: ") + acct.getName() + " (" + acct.getRoleName() + ")", LogStatus.SUCCESS);
                changed();
            });
            menu.add(on);
        }
        if (archived) {
            JMenuItem restore = new JMenuItem("Restore (keep inactive)");
            restore.addActionListener(e -> {
                if (refused(store.setAccountArchived(acct, false))) return;
                store.addLog(adminLabel(), "Account Restored from Archive: " + acct.getName() + " (" + acct.getRoleName() + ")", LogStatus.SUCCESS);
                changed();
            });
            menu.add(restore);
        } else {
            JMenuItem arch = new JMenuItem("Archive");
            arch.setEnabled(!active);
            arch.setToolTipText(active ? "Deactivate the account first" : "Tuck this deactivated account away");
            arch.addActionListener(e -> {
                if (refused(store.setAccountArchived(acct, true))) return;
                store.addLog(adminLabel(), "Account Archived: " + acct.getName() + " (" + acct.getRoleName() + ")", LogStatus.SUCCESS);
                changed();
            });
            menu.add(arch);
        }
        menu.show(anchor, 0, anchor.getHeight() + 2);
    }

    /** Asks for a new password (at least 6 characters) and saves it hashed. */
    private void openResetPassword(Account acct) {
        JPasswordField pw = new JPasswordField(16);
        int ok = JOptionPane.showConfirmDialog(this, pw, "Set a new password for " + acct.getName(), JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            String newPw = new String(pw.getPassword()).trim();
            if (newPw.length() < DemoDataStore.MIN_PASSWORD_LENGTH) {
                JOptionPane.showMessageDialog(this, "Password must be at least " + DemoDataStore.MIN_PASSWORD_LENGTH + " characters.", "Reset Password", JOptionPane.WARNING_MESSAGE);
                return;
            }
            store.changeAccountPassword(acct, newPw);
            store.addLog((parent != null && store.getCurrentAdmin() != null ? store.getCurrentAdmin().getName() : "Admin") + " (administrator)",
                    "Password Reset: " + acct.getName(), LogStatus.SUCCESS);
            JOptionPane.showMessageDialog(this, "Password updated for " + acct.getName() + ".", "Reset Password", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Lets Admin manually add or hide specific modules for one student, on top of the normal
     * year-level match (see Student.isEnrolledIn(Module)). A checkbox per module in the current
     * catalog; ticking/unticking one only ever changes the override that module actually needs --
     * a year-level match gets a "drop" override when unticked, a non-match gets an "extra"
     * override when ticked -- so nothing is written for boxes the admin left alone.
     */
    private void openEnrollmentDialog(Student student) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Manage Enrollment \u2014 " + student.getName(), Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        dlg.setSize(500, 600);
        dlg.setLocationRelativeTo(this);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel who = new JLabel(student.getName() + "  \u2022  " + student.getId());
        who.setFont(new Font("SansSerif", Font.BOLD, 14));
        who.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(who);
        content.add(Box.createVerticalStrut(10));

        // Year level lives here too, so Admin can fix an account whose year was never set (or changes at promotion).
        JPanel yearRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        yearRow.setOpaque(false);
        yearRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel yearLbl = new JLabel("Year level:");
        yearLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JComboBox<YearLevel> yearCombo = new JComboBox<>(YearLevel.values());
        yearCombo.setSelectedItem(student.getYearLevel()); // null -> nothing selected
        yearRow.add(yearLbl);
        yearRow.add(yearCombo);
        content.add(yearRow);

        JLabel hint = new JLabel("<html><span style='color:#777777;font-size:10px'>Modules matching their year level are checked "
                + "automatically. Untick one to hide it for this student only, or tick a module from a "
                + "different year to add it as an exception (e.g. a repeat subject).</span></html>");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(8, 0, 12, 0));
        content.add(hint);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        List<Module> sorted = new ArrayList<>(store.getModules());
        sorted.sort((x, y) -> {
            int c = String.valueOf(x.getYearLevel()).compareTo(String.valueOf(y.getYearLevel()));
            if (c == 0) c = String.valueOf(x.getSemester()).compareTo(String.valueOf(y.getSemester()));
            return c != 0 ? c : x.getCode().compareTo(y.getCode());
        });

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAll();
            for (Module m : sorted) {
                boolean normallyMatches = student.getYearLevel() != null && student.getYearLevel() == m.getYearLevel();
                JCheckBox box = new JCheckBox(UIKit.displayCode(m.getCode()) + " \u2014 " + m.getTitle()
                        + "  (" + (m.getYearLevel() != null ? m.getYearLevel().toString() : "no year") + ", " + m.getSemester() + ")");
                box.setFont(new Font("SansSerif", Font.PLAIN, 12));
                box.setAlignmentX(Component.LEFT_ALIGNMENT);
                box.setSelected(student.isEnrolledIn(m));
                box.addActionListener(e -> {
                    if (box.isSelected()) {
                        if (normallyMatches) store.undropModuleForStudent(student.getId(), m.getCode());
                        else store.addEnrollmentOverride(student.getId(), m.getCode());
                    } else {
                        if (normallyMatches) store.dropModuleForStudent(student.getId(), m.getCode());
                        else store.removeEnrollmentOverride(student.getId(), m.getCode());
                    }
                });
                list.add(box);
                list.add(Box.createVerticalStrut(4));
            }
            list.revalidate();
            list.repaint();
        };
        rebuild[0].run();

        yearCombo.addActionListener(e -> {
            YearLevel picked = (YearLevel) yearCombo.getSelectedItem();
            if (picked != null && picked != student.getYearLevel()) {
                store.setStudentYearLevel(student.getId(), picked);
                rebuild[0].run();
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(scroll);

        dlg.add(content, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        UIKit.RoundButton close = new UIKit.RoundButton("Done", UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 8);
        close.addActionListener(e -> {
            dlg.dispose();
            if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
            refresh();
        });
        bottom.add(close);
        dlg.add(bottom, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    /** The "Current Term" control: same black pill as "Add Account", with a small white chevron. */
    private static class TermButton extends UIKit.RoundButton {
        TermButton(String text) {
            super(text, UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 8);
            setBorder(new EmptyBorder(9, 18, 9, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float x = getWidth() - 19f, y = getHeight() / 2f;
            java.awt.geom.Path2D chevron = new java.awt.geom.Path2D.Float();
            chevron.moveTo(x - 4f, y - 2f);
            chevron.lineTo(x, y + 2f);
            chevron.lineTo(x + 4f, y - 2f);
            g2.draw(chevron);
            g2.dispose();
        }
    }


    /**
     * Opens the Add Account window and saves the new account through the store, which rejects duplicate
     * IDs and e-mails.
     */
    private void openAddAccount() {
        AddAccountDialog dlg = new AddAccountDialog(SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        if (!dlg.isConfirmed()) return;
        String problem;
        switch (dlg.getRole()) {
            case "Staff":
                problem = store.addStaffAccount(new Staff(dlg.getIdField(), dlg.getFullName(), dlg.getEmail(), dlg.getPassword(), dlg.getAux1(), dlg.getAux2()));
                break;
            case "Admin":
                problem = store.addAdminAccount(new Admin(dlg.getIdField(), dlg.getFullName(), dlg.getEmail(), dlg.getPassword(), dlg.getAux2()));
                break;
            default:
                Student newStudent = new Student(dlg.getIdField(), dlg.getFullName(), dlg.getEmail(), dlg.getPassword());
                newStudent.setYearLevel(dlg.getYearLevel());
                problem = store.addStudentAccount(newStudent);
                break;
        }
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, "Account not added", JOptionPane.WARNING_MESSAGE);
            return;
        }
        store.addLog(adminLabel(), "Account Created: " + dlg.getFullName() + " (" + dlg.getRole() + ")", LogStatus.SUCCESS);
        changed();
    }

    /** Re-reads the accounts and rebuilds the stat cards and the table using the search and filters. */
    public void refresh() {
        List<Account> all = allAccounts();
        int active = 0, inactive = 0, archived = 0;
        for (Account a : all) {
            if (a.isArchived()) archived++;
            else if (a.isActive()) active++;
            else inactive++;
        }
        page.statsRow.removeAll();
        page.statsRow.add(InventoryPanel.statCard("Total Accounts", all.size() + " Accounts", InventoryPanel.STAT_TOTAL));
        page.statsRow.add(InventoryPanel.statCard("Active", active + " Active", InventoryPanel.STAT_READY));
        page.statsRow.add(InventoryPanel.statCard("Inactive (can be archived)", inactive + " Inactive", InventoryPanel.STAT_PENDING));
        page.statsRow.add(InventoryPanel.statCard("Archived", archived + " Archived", InventoryPanel.SLATE));
        page.statsRow.revalidate();
        page.statsRow.repaint();

        String q = searchBox.field.getText().trim().toLowerCase();
        String role = roleFilter.getSelected(), st = statusFilter.getSelected();
        tableBody.removeAll();
        tableBody.add(InventoryPanel.headerBand(LABELS, MINW, WEIGHT, ALIGN, 1));
        int shown = 0;
        for (Account acct : all) {
            boolean arch = acct.isArchived(), act = acct.isActive();
            if (!"All".equals(role) && !role.equals(acct.getRoleName())) continue;
            if (S_ALL.equals(st) && arch) continue;
            if (S_ACTIVE.equals(st) && !(act && !arch)) continue;
            if (S_INACTIVE.equals(st) && !(!act && !arch)) continue;
            if (S_ARCHIVED.equals(st) && !arch) continue;
            if (!q.isEmpty() && !(acct.getName().toLowerCase().contains(q) || acct.getLoginId().toLowerCase().contains(q)
                    || acct.getEmail().toLowerCase().contains(q))) continue;
            tableBody.add(buildRow(acct));
            shown++;
        }
        if (shown == 0) tableBody.add(InventoryPanel.emptyLine(S_ARCHIVED.equals(st) ? "No archived accounts." : "No accounts match your filters."));
        tableBody.revalidate();
        tableBody.repaint();
    }

    /** Modal for creating a new account of any role. */
    static class AddAccountDialog extends JDialog {
        /** True after the user clicked Create. */
        private boolean confirmed = false;
        /** Text inputs: name, e-mail, ID and the first extra field (department). */
        private JTextField nameField, emailField, idField, aux1Field;
        /** The initial password (at least 6 characters). */
        private JPasswordField passwordField;
        /** Student / Staff / Admin. */
        private JComboBox<String> roleCombo;
        /** Year level (students only). */
        private JComboBox<YearLevel> yearLevelCombo;
        /** Labels that change text with the chosen role. */
        private JLabel idLabel, aux1Label, aux2Label, yearLevelLabel, idHint;
        /** Second extra field (job title). */
        private JTextField aux2Field;

        AddAccountDialog(Window owner) {
            super(owner, "Add Account", ModalityType.APPLICATION_MODAL);
            setSize(420, 540);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());

            JPanel content = new JPanel();
            content.setBorder(new EmptyBorder(16, 16, 16, 16));
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            content.add(new JLabel("Role"));
            roleCombo = new JComboBox<>(new String[]{"Student", "Staff", "Admin"});
            content.add(roleCombo);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Full Name"));
            nameField = new JTextField();
            content.add(nameField);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Email"));
            emailField = new JTextField();
            content.add(emailField);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Password"));
            passwordField = new JPasswordField();
            content.add(passwordField);
            content.add(Box.createVerticalStrut(8));

            idLabel = new JLabel("Student ID");
            content.add(idLabel);
            idField = new JTextField();
            content.add(idField);
            idHint = new JLabel("Format: 00-0000-000000 (e.g. 02-2526-015176)");
            idHint.setFont(new Font("SansSerif", Font.PLAIN, 10));
            idHint.setForeground(new Color(140, 140, 140));
            content.add(idHint);
            content.add(Box.createVerticalStrut(8));

            yearLevelLabel = new JLabel("Year Level");
            content.add(yearLevelLabel);
            yearLevelCombo = new JComboBox<>(YearLevel.values());
            yearLevelCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, yearLevelCombo.getPreferredSize().height));
            content.add(yearLevelCombo);
            content.add(Box.createVerticalStrut(8));

            aux1Label = new JLabel("Department");
            content.add(aux1Label);
            aux1Field = new JTextField();
            content.add(aux1Field);
            content.add(Box.createVerticalStrut(8));

            aux2Label = new JLabel("Title / Position");
            content.add(aux2Label);
            aux2Field = new JTextField();
            content.add(aux2Field);

            roleCombo.addActionListener(e -> updateFieldsForRole());
            updateFieldsForRole();

            add(content, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancel = new JButton("Cancel");
            JButton save = new JButton("Add");
            buttons.add(cancel);
            buttons.add(save);
            add(buttons, BorderLayout.SOUTH);

            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> {
                if (nameField.getText().trim().isEmpty() || emailField.getText().trim().isEmpty()
                        || new String(passwordField.getPassword()).trim().isEmpty() || idField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Name, email, password, and ID are required.", "Missing info", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (new String(passwordField.getPassword()).trim().length() < DemoDataStore.MIN_PASSWORD_LENGTH) {
                    JOptionPane.showMessageDialog(this, "Password must be at least " + DemoDataStore.MIN_PASSWORD_LENGTH + " characters.", "Password too short", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if ("Student".equals(roleCombo.getSelectedItem()) && !UIKit.isValidSchoolId(idField.getText().trim())) {
                    JOptionPane.showMessageDialog(this, "Student ID must be in the format 00-0000-000000 (e.g. 02-2526-015176).",
                            "Invalid Student ID", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                confirmed = true;
                dispose();
            });
        }

        /** Shows the right labels and fields for the chosen role. */
        private void updateFieldsForRole() {
            String role = (String) roleCombo.getSelectedItem();
            boolean isStudent = "Student".equals(role);
            boolean isStaff = "Staff".equals(role);
            boolean isAdmin = "Admin".equals(role);

            idLabel.setText(isStudent ? "Student ID" : isStaff ? "Staff ID" : "Admin ID");
            idHint.setVisible(isStudent);

            yearLevelLabel.setVisible(isStudent);
            yearLevelCombo.setVisible(isStudent);

            aux1Label.setVisible(isStaff);
            aux1Field.setVisible(isStaff);
            aux1Label.setText("Department");

            aux2Label.setVisible(isStaff || isAdmin);
            aux2Field.setVisible(isStaff || isAdmin);
            aux2Label.setText(isStaff ? "Title / Position" : "Title");

            revalidate();
            repaint();
        }

        /** True when the user created the account (not cancelled). */
        boolean isConfirmed() { return confirmed; }
        /** "Student", "Staff" or "Admin". */
        String getRole() { return (String) roleCombo.getSelectedItem(); }
        /** The typed name. */
        String getFullName() { return nameField.getText().trim(); }
        /** The typed e-mail. */
        String getEmail() { return emailField.getText().trim(); }
        /** The typed password (plain; the store hashes it). */
        String getPassword() { return new String(passwordField.getPassword()); }
        /** The typed ID (School ID for a student). */
        String getIdField() { return idField.getText().trim(); }
        /** The chosen year level (students only). */
        YearLevel getYearLevel() { return (YearLevel) yearLevelCombo.getSelectedItem(); }
        /** The first extra value (department). */
        String getAux1() { return aux1Field.getText().trim(); }
        /** The second extra value (job title). */
        String getAux2() { return aux2Field.getText().trim(); }
    }
}
