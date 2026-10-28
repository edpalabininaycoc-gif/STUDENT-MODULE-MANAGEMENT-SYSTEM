package moduledistributionsystem.ui.admin;

import moduledistributionsystem.ui.common.UIKit;
import moduledistributionsystem.ui.staff.InventoryPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.LogStatus;
import moduledistributionsystem.model.NotificationItem;
import moduledistributionsystem.model.QueryPriority;
import moduledistributionsystem.model.QueryStatus;
import moduledistributionsystem.model.StudentQuery;

/**
 * Admin: full visibility over every appointment in the system, with the ability to override its status directly
 * (escalations, reinstating a wrongly-declined booking, correcting a mistaken "Picked Up"), plus the escalated
 * queries. Same page design as the staff pages. Appointments cannot be archived or deleted (not allowed).
 */
public class AppointmentsOverridePanel extends JPanel {
    /** Names of the two tabs. */
    private static final String TAB_ALL = "All Appointments", TAB_ESC = "Escalated Queries";

    /** Column titles of the appointments table. */
    private static final String[] LABELS = {"STUDENT NAME", "STUDENT ID", "MODULE", "SCHEDULE", "OFFICER", "STATUS", "ACTIONS"};
    /** Minimum width of each column of the appointments table. */
    private static final int[] MINW   = {140, 112, 190, 124, 110, 92, 150};
    /** How the free width is shared between the appointment columns. */
    private static final int[] WEIGHT = {5, 4, 9, 5, 4, 3, 5};
    private static final int[] ALIGN  = {InventoryPanel.LEFT, InventoryPanel.CENTER, InventoryPanel.LEFT, InventoryPanel.CENTER,
            InventoryPanel.LEFT, InventoryPanel.CENTER, InventoryPanel.RIGHT};

    /** Column titles of the escalated-queries table. */
    private static final String[] LABELS_Q = {"TICKET", "STUDENT", "MODULE", "CATEGORY", "PRIORITY", "SUBMITTED", "ACTION"};
    /** Minimum width of each column of the queries table. */
    private static final int[] MINW_Q   = {70, 150, 90, 130, 80, 120, 80};
    /** How the free width is shared between the query columns. */
    private static final int[] WEIGHT_Q = {2, 6, 3, 5, 3, 5, 3};
    private static final int[] ALIGN_Q  = {InventoryPanel.LEFT, InventoryPanel.LEFT, InventoryPanel.CENTER, InventoryPanel.LEFT,
            InventoryPanel.CENTER, InventoryPanel.CENTER, InventoryPanel.CENTER};

    /** The shared data store. */
    private DemoDataStore store;
    /** The admin window. */
    private AdminMainFrame parent;
    /** The shared page skeleton. */
    private final InventoryPanel.Page page;
    /** The All Appointments / Escalated Queries tabs. */
    private final InventoryPanel.TabBar tabs;
    private final JPanel tableBody = new JPanel();
    /** Search by student or module. */
    private InventoryPanel.SearchBox searchBox;
    /** Filter by appointment status. */
    private InventoryPanel.Dropdown statusFilter;
    private final JLabel filterNote = new JLabel(" ");
    private String studentFilterId = null;   // when set, only this student's appointments are listed
    /** Name of the student whose appointments are shown (set by "View appointments" in a ticket). */
    private String studentFilterName = null;

    /** Builds the page: header, stat cards, tabs, filters and table. */
    public AppointmentsOverridePanel(DemoDataStore store, AdminMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(InventoryPanel.PAGE_BG);

        page = new InventoryPanel.Page(AdminMainFrame.header(store, "Appointments",
                "Review every booking, override status, and handle escalated queries", "Appointment Oversight"), 5);
        add(page.root, BorderLayout.CENTER);

        tabs = new InventoryPanel.TabBar(TAB_ALL, TAB_ESC);
        tabs.setBorder(new EmptyBorder(0, 0, 12, 0));
        tabs.setOnChange(() -> {
            if (!TAB_ALL.equals(tabs.getSelected())) { studentFilterId = null; studentFilterName = null; }
            refresh();
        });

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(tabs);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setOpaque(false);
        filters.setBorder(new EmptyBorder(0, 0, 12, 0));
        searchBox = new InventoryPanel.SearchBox("Search student or module...", 240);
        statusFilter = new InventoryPanel.Dropdown("Status: ");
        Set<String> sts = new LinkedHashSet<>();
        sts.add("All");
        sts.add("Pending");
        sts.add("Approved");
        sts.add("Picked Up");
        sts.add("Declined");
        statusFilter.setItems(sts);
        filters.add(searchBox);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(statusFilter);
        filters.add(Box.createHorizontalStrut(14));
        filterNote.setFont(new Font("SansSerif", Font.PLAIN, 12));
        filterNote.setForeground(InventoryPanel.SLATE);
        filters.add(filterNote);
        filters.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(filters);
        page.card.add(north, BorderLayout.NORTH);

        tableBody.setLayout(new BoxLayout(tableBody, BoxLayout.Y_AXIS));
        tableBody.setOpaque(false);
        page.card.add(tableBody, BorderLayout.CENTER);

        searchBox.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        statusFilter.setOnChange(this::refresh);
        refresh();
    }

    /** "Name (administrator)" text used in the log lines. */
    private String adminLabel() {
        return (store.getCurrentAdmin() != null ? store.getCurrentAdmin().getName() : "Admin") + " (administrator)";
    }

    /** Refreshes this page and the other pages after a change. */
    private void changed() {
        if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
        refresh();
    }

    /** The colored status badge of an appointment. */
    private static InventoryPanel.StatusBadge statusBadge(AppointmentStatus s) {
        switch (s) {
            case CONFIRMED: return new InventoryPanel.StatusBadge("Approved", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129));
            case COMPLETED: return new InventoryPanel.StatusBadge("Picked Up", UIKit.BLUE_PILL_BG, UIKit.BLUE_PILL_FG, new Color(59, 130, 246));
            case CANCELLED: return new InventoryPanel.StatusBadge("Declined", new Color(254, 226, 226), new Color(153, 27, 27), new Color(239, 68, 68));
            default: return new InventoryPanel.StatusBadge("Pending", new Color(254, 243, 199), new Color(133, 50, 10), new Color(245, 158, 11));
        }
    }

    /** Readable name of an appointment status. */
    private static String statusName(AppointmentStatus s) {
        switch (s) {
            case CONFIRMED: return "Approved";
            case COMPLETED: return "Picked Up";
            case CANCELLED: return "Declined";
            default: return "Pending";
        }
    }

    /** The colored priority badge of a ticket. */
    private static InventoryPanel.StatusBadge priorityBadge(QueryPriority p) {
        switch (p) {
            case HIGH: return new InventoryPanel.StatusBadge("High", new Color(254, 226, 226), new Color(153, 27, 27), new Color(239, 68, 68));
            case MEDIUM: return new InventoryPanel.StatusBadge("Medium", new Color(254, 243, 199), new Color(133, 50, 10), new Color(245, 158, 11));
            default: return new InventoryPanel.StatusBadge("Low", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129));
        }
    }

    // ------------------------------------------------------------------ rows

    /** Builds one appointment row with its Override button. */
    private JComponent appointmentRow(Appointment a) {
        InventoryPanel.RowPanel row = new InventoryPanel.RowPanel(InventoryPanel.ROW_H, true, MINW, WEIGHT, ALIGN, 2);
        row.add(InventoryPanel.cellLabel(a.getStudentName(), true, InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(a.getStudentId(), false, InventoryPanel.SLATE));
        row.add(InventoryPanel.cellLabel(a.getModule().getDisplayCode() + "  " + a.getModule().getTitle(), false, InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(a.getDateTime().format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)), false, InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(a.getOfficer(), false, InventoryPanel.SLATE));
        row.add(statusBadge(a.getStatus()));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        UIKit.RoundButton override = InventoryPanel.rowButton("Override", true);
        override.addActionListener(e -> openOverrideDialog(a));
        actions.add(override);
        row.add(actions);
        return row;
    }

    /** Builds one escalated-ticket row with its Review button. */
    private JComponent queryRow(StudentQuery q) {
        InventoryPanel.RowPanel row = new InventoryPanel.RowPanel(InventoryPanel.ROW_H, true, MINW_Q, WEIGHT_Q, ALIGN_Q, 1);
        row.add(InventoryPanel.cellLabel("#T-" + q.getTicketNumber(), true, InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(q.getStudentName(), false, InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(UIKit.displayCode(q.getSubjectCode()), false, InventoryPanel.SLATE));
        row.add(InventoryPanel.cellLabel(q.getCategory(), false, new Color(40, 100, 200)));
        row.add(priorityBadge(q.getPriority()));
        row.add(InventoryPanel.cellLabel(q.getSubmittedAt().format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)), false, InventoryPanel.SEMESTER_TXT));
        UIKit.RoundButton review = InventoryPanel.rowButton("Review", true);
        review.addActionListener(e -> openTicket(q));
        row.add(review);
        return row;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Lets the administrator choose a new status. The store applies the stock rules (a pickup is
     * refused when no copy is left), the student is notified and the change is logged.
     */
    private void openOverrideDialog(Appointment a) {
        JComboBox<AppointmentStatus> statusCombo = new JComboBox<>(AppointmentStatus.values());
        statusCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : statusName((AppointmentStatus) value), index, sel, focus);
            }
        });
        statusCombo.setSelectedItem(a.getStatus());
        int ok = JOptionPane.showConfirmDialog(this, statusCombo,
                "Override status for " + a.getStudentName() + "'s " + a.getModule().getDisplayCode() + " appointment",
                JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        AppointmentStatus newStatus = (AppointmentStatus) statusCombo.getSelectedItem();
        if (newStatus == a.getStatus()) return;
        AppointmentStatus old = a.getStatus();
        if (!store.updateAppointmentStatus(a, newStatus)) {
            JOptionPane.showMessageDialog(this, newStatus == AppointmentStatus.COMPLETED && a.getModule().getStocks() <= 0
                    ? a.getModule().getDisplayCode() + " is out of stock, so it can't be marked picked up. Restock the module first."
                    : "The status could not be changed.", "Override not applied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        store.addNotification(new NotificationItem(a.getStudentId(), "Appointment Updated by Admin",
                "Your appointment for " + a.getModule().getDisplayCode() + " was changed from "
                        + statusName(old) + " to " + statusName(newStatus) + " by an administrator."));
        store.addLog(adminLabel(), "Appointment Override: " + a.getStudentName() + " (" + a.getModule().getCode() + ") "
                + statusName(old) + " \u2192 " + statusName(newStatus), LogStatus.SUCCESS);
        changed();
    }

    /** Opens an escalated ticket so the administrator can reply, or jump to the student's appointments. */
    private void openTicket(StudentQuery q) {
        TicketDialog dlg = new TicketDialog(SwingUtilities.getWindowAncestor(this), q);
        dlg.setVisible(true);
        if (dlg.isResolved()) {
            store.saveQuery(q);
            store.addNotification(new NotificationItem(q.getStudentId(), "Query Answered \u2014 Ticket #T-" + q.getTicketNumber(),
                    "An administrator replied to your \"" + q.getCategory() + "\" query: " + q.getReply()));
            store.addLog(adminLabel(), "Escalated Ticket Resolved: #T-" + q.getTicketNumber() + " (" + q.getStudentName() + ")", LogStatus.SUCCESS);
            changed();
        } else if (dlg.wantsAppointments()) {
            studentFilterId = q.getStudentId();
            studentFilterName = q.getStudentName();
            tabs.select(TAB_ALL);
            refresh();
        }
    }

    // ------------------------------------------------------------------ refresh

    /** Re-reads the data and rebuilds the stat cards and the table of the chosen tab. */
    public void refresh() {
        List<Appointment> all = store.getAppointments();
        long pending = all.stream().filter(a -> a.getStatus() == AppointmentStatus.PENDING).count();
        long confirmed = all.stream().filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED).count();
        long cancelled = all.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
        List<StudentQuery> escalated = new ArrayList<>();
        for (StudentQuery q : store.getQueries()) if (q.getStatus() == QueryStatus.ESCALATED) escalated.add(q);

        page.statsRow.removeAll();
        page.statsRow.add(InventoryPanel.statCard("Total Appointments", all.size() + " Total", InventoryPanel.STAT_TOTAL));
        page.statsRow.add(InventoryPanel.statCard("Pending", pending + " Pending", InventoryPanel.STAT_PENDING));
        page.statsRow.add(InventoryPanel.statCard("Approved", confirmed + " Approved", InventoryPanel.STAT_READY));
        page.statsRow.add(InventoryPanel.statCard("Declined / Cancelled", cancelled + " Declined", InventoryPanel.DELETE_RED));
        page.statsRow.add(InventoryPanel.statCard("Escalated Queries", escalated.size() + " Open", escalated.isEmpty() ? InventoryPanel.INK : InventoryPanel.DELETE_RED));
        page.statsRow.revalidate();
        page.statsRow.repaint();
        tabs.setCount(TAB_ESC, escalated.size());

        String tab = tabs.getSelected();
        statusFilter.setVisible(!TAB_ESC.equals(tab));
        filterNote.setText(TAB_ALL.equals(tab) && studentFilterId != null ? "Showing appointments for " + studentFilterName + " (switch tabs to clear)" : " ");

        String q = searchBox.field.getText().trim().toLowerCase();
        String st = statusFilter.getSelected();
        tableBody.removeAll();
        int shown = 0;
        String emptyMsg;
        if (TAB_ESC.equals(tab)) {
            tableBody.add(InventoryPanel.headerBand(LABELS_Q, MINW_Q, WEIGHT_Q, ALIGN_Q, 1));
            List<StudentQuery> sorted = new ArrayList<>(escalated);
            sorted.sort(Comparator.comparing(StudentQuery::getPriority).reversed().thenComparing(StudentQuery::getSubmittedAt));
            for (StudentQuery sq : sorted) {
                if (!q.isEmpty() && !(sq.getStudentName() + " #t-" + sq.getTicketNumber() + " " + sq.getSubjectCode() + " " + sq.getCategory()).toLowerCase().contains(q)) continue;
                tableBody.add(queryRow(sq));
                shown++;
            }
            emptyMsg = escalated.isEmpty() ? "No escalated tickets. Nothing needs the administrator's attention right now." : "No tickets match your search.";
        } else {
            tableBody.add(InventoryPanel.headerBand(LABELS, MINW, WEIGHT, ALIGN, 2));
            List<Appointment> sorted = new ArrayList<>(all);
            sorted.sort((x, y) -> y.getRequestedAt().compareTo(x.getRequestedAt()));
            for (Appointment a : sorted) {
                if (studentFilterId != null && !studentFilterId.equals(a.getStudentId())) continue;
                if (!"All".equals(st) && !st.equals(statusName(a.getStatus()))) continue;
                if (!q.isEmpty() && !(a.getStudentName() + " " + a.getStudentId() + " " + a.getModule().getCode() + " "
                        + a.getModule().getDisplayCode() + " " + a.getModule().getTitle()).toLowerCase().contains(q)) continue;
                tableBody.add(appointmentRow(a));
                shown++;
            }
            emptyMsg = studentFilterId != null ? "This student has no appointments." : "No appointments match your filters.";
        }
        if (shown == 0) tableBody.add(InventoryPanel.emptyLine(emptyMsg));
        tableBody.revalidate();
        tableBody.repaint();
    }

    /** Modal to read an escalated ticket, reply to the student and resolve it. */
    static class TicketDialog extends JDialog {
        /** True when the administrator sent a reply. */
        private boolean resolved = false;
        /** True when the administrator chose "View appointments". */
        private boolean openAppointments = false;

        TicketDialog(Window owner, StudentQuery q) {
            super(owner, "Escalated Ticket #T-" + q.getTicketNumber(), ModalityType.APPLICATION_MODAL);
            setSize(500, 540);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());

            JPanel content = new JPanel();
            content.setBorder(new EmptyBorder(16, 16, 16, 16));
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            JLabel who = new JLabel(q.getStudentName() + " \u2014 " + q.getCategory());
            who.setFont(new Font("SansSerif", Font.BOLD, 14));
            who.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(who);
            JLabel meta = new JLabel("Student ID " + q.getStudentId() + "  \u00B7  Module " + q.getSubjectCode()
                    + "  \u00B7  " + q.getPriority() + " priority  \u00B7  "
                    + q.getSubmittedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)));
            meta.setFont(new Font("SansSerif", Font.PLAIN, 11));
            meta.setForeground(UIKit.TEXT_MUTED);
            meta.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(meta);
            content.add(Box.createVerticalStrut(10));

            JLabel msgLabel = new JLabel("Student's message");
            msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(msgLabel);
            JTextArea msg = new JTextArea(q.getMessage(), 4, 30);
            msg.setEditable(false);
            msg.setLineWrap(true);
            msg.setWrapStyleWord(true);
            JScrollPane msgScroll = new JScrollPane(msg);
            msgScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(msgScroll);
            content.add(Box.createVerticalStrut(10));

            if (!q.getReply().isEmpty()) {
                JLabel prevLabel = new JLabel("Staff notes so far");
                prevLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(prevLabel);
                JTextArea prev = new JTextArea(q.getReply(), 2, 30);
                prev.setEditable(false);
                prev.setLineWrap(true);
                prev.setWrapStyleWord(true);
                JScrollPane prevScroll = new JScrollPane(prev);
                prevScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(prevScroll);
                content.add(Box.createVerticalStrut(10));
            }

            JLabel replyLabel = new JLabel("Administrator reply (sent to the student)");
            replyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(replyLabel);
            JTextArea reply = new JTextArea(5, 30);
            reply.setLineWrap(true);
            reply.setWrapStyleWord(true);
            JScrollPane replyScroll = new JScrollPane(reply);
            replyScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(replyScroll);
            content.add(Box.createVerticalStrut(6));

            JLabel hint = new JLabel("<html><div style='width:420px'>Need to change the pickup schedule instead? Use "
                    + "\"View Student's Appointments\" to override the booking, then come back and reply.</div></html>");
            hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
            hint.setForeground(UIKit.TEXT_MUTED);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(hint);

            add(content, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton close = new JButton("Close");
            JButton appts = new JButton("View Student's Appointments");
            JButton resolve = new JButton("Reply & Resolve");
            buttons.add(close);
            buttons.add(appts);
            buttons.add(resolve);
            add(buttons, BorderLayout.SOUTH);

            close.addActionListener(e -> dispose());
            appts.addActionListener(e -> {
                openAppointments = true;
                dispose();
            });
            resolve.addActionListener(e -> {
                String text = reply.getText().trim();
                if (text.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Write a reply for the student before resolving the ticket.",
                            "Reply required", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                q.setReply("(Administrator) " + text);
                q.setStatus(QueryStatus.ANSWERED);
                resolved = true;
                dispose();
            });
        }

        /** True when a reply was sent. */
        boolean isResolved() { return resolved; }
        /** True when the administrator wants to see the student's appointments. */
        boolean wantsAppointments() { return openAppointments; }
    }
}
