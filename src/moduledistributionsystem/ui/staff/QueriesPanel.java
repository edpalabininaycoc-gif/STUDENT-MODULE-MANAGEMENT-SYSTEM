package moduledistributionsystem.ui.staff;

import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.NotificationItem;
import moduledistributionsystem.model.QueryPriority;
import moduledistributionsystem.model.QueryStatus;
import moduledistributionsystem.model.StudentQuery;

/**
 * Staff page "Inquiries Desk": the student queries (tickets), with stat cards, a search box and a
 * status filter. Staff open a ticket to read it, answer it or escalate it to the administrator; the
 * student is notified of the answer.
 *
 * Same layout and styling as the other staff pages (header, stat cards, one gray card holding the
 * toolbar + table). Staff can NOT archive or delete tickets (not allowed), so every ticket stays in the
 * list.
 */
public class QueriesPanel extends JPanel {
    /** Column titles of the tickets table. */
    private static final String[] LABELS = {"", "TICKET #", "STUDENT NAME", "SUBJECT CODE", "CATEGORY", "PRIORITY", "SUBMITTED", "STATUS", "ACTION"};
    /** Minimum width of each column. */
    private static final int[] MINW   = {40, 76, 160, 100, 130, 80, 120, 100, 80};
    /** How the free width is shared between the columns. */
    private static final int[] WEIGHT = {1, 3, 6, 4, 5, 3, 5, 4, 3};
    private static final int[] ALIGN  = {InventoryPanel.CENTER, InventoryPanel.LEFT, InventoryPanel.LEFT, InventoryPanel.CENTER,
            InventoryPanel.CENTER, InventoryPanel.CENTER, InventoryPanel.CENTER, InventoryPanel.CENTER, InventoryPanel.CENTER};

    /** The shared data store. */
    private final DemoDataStore store;
    /** The staff window. */
    private final StaffMainFrame parent;
    /** The row of statistic cards. */
    private JPanel statsRow;
    /** Holds the header band and the ticket rows. */
    private JPanel tableBody;
    /** Search by ticket, student or subject. */
    private InventoryPanel.SearchBox searchBox;
    /** Filter by ticket status. */
    private InventoryPanel.Dropdown statusFilter;
    /** Header checkbox that selects every shown row. */
    private JCheckBox selectAll;
    private final Map<StudentQuery, JCheckBox> checks = new LinkedHashMap<>();

    /** Builds the page: header, stat cards, toolbar and table. */
    public QueriesPanel(DemoDataStore store, StaffMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(InventoryPanel.PAGE_BG);

        JPanel content = new UIKit.ScrollableBoxPanel();
        content.setBorder(new EmptyBorder(20, 22, 22, 22));

        JComponent header = InventoryPanel.buildStaffHeader(store, "Inquiries Desk",
                "SMMS Support & Student Care Hub", "Academic Inquiries & Escalations");
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(header);
        content.add(Box.createVerticalStrut(12));

        JPanel divider = new JPanel();
        divider.setBackground(InventoryPanel.HEADER_LINE);
        divider.setOpaque(true);
        divider.setPreferredSize(new Dimension(10, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(divider);
        content.add(Box.createVerticalStrut(16));

        statsRow = new JPanel(new GridLayout(1, 4, 14, 0));
        statsRow.setOpaque(false);
        statsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsRow.setPreferredSize(new Dimension(10, 66));
        statsRow.setMinimumSize(new Dimension(10, 66));
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        content.add(statsRow);
        content.add(Box.createVerticalStrut(16));

        UIKit.RoundPanel tableCard = new UIKit.RoundPanel(12, InventoryPanel.CARD);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(17, 15, 17, 15));
        tableCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableCard.add(buildToolbar(), BorderLayout.NORTH);

        tableBody = new JPanel();
        tableBody.setLayout(new BoxLayout(tableBody, BoxLayout.Y_AXIS));
        tableBody.setOpaque(false);
        tableCard.add(tableBody, BorderLayout.CENTER);
        content.add(tableCard);

        add(UIKit.scroll(content), BorderLayout.CENTER);

        searchBox.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        statusFilter.setOnChange(this::refresh);

        refresh();
    }

    // ------------------------------------------------------------------ toolbar

    /** Builds the search box and the status filter. */
    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 15, 0));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setOpaque(false);
        searchBox = new InventoryPanel.SearchBox("Search student, ticket, or module...", 280);
        statusFilter = new InventoryPanel.Dropdown("Status: ");
        Set<String> statuses = new LinkedHashSet<>();
        statuses.add("All");
        statuses.add("Pending");
        statuses.add("Answered");
        statuses.add("Escalated");
        statusFilter.setItems(statuses);
        filters.add(searchBox);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(statusFilter);
        toolbar.add(filters, BorderLayout.WEST);

        return toolbar;
    }

    // ------------------------------------------------------------------ actions

    /** The checked tickets. */
    private List<StudentQuery> selected() {
        List<StudentQuery> out = new ArrayList<>();
        for (Map.Entry<StudentQuery, JCheckBox> en : checks.entrySet()) if (en.getValue().isSelected()) out.add(en.getKey());
        return out;
    }

    // ------------------------------------------------------------------ table

    /** Builds the gray column-title band. */
    private JComponent buildHeaderRow() {
        InventoryPanel.RowPanel band = new InventoryPanel.RowPanel(InventoryPanel.BAND_H, false, MINW, WEIGHT, ALIGN, 2);
        band.setOpaque(true);
        band.setBackground(InventoryPanel.BAND);
        for (int i = 0; i < LABELS.length; i++) {
            if (i == 0) {
                selectAll = new JCheckBox();
                selectAll.setOpaque(false);
                selectAll.setToolTipText("Select all shown");
                selectAll.addActionListener(e -> { for (JCheckBox cb : checks.values()) cb.setSelected(selectAll.isSelected()); });
                band.add(selectAll);
                continue;
            }
            JLabel l = new JLabel(LABELS[i]);
            l.setFont(new Font("SansSerif", Font.BOLD, 10));
            l.setForeground(Color.BLACK);
            band.add(l);
        }
        return band;
    }

    /** Builds one ticket row. */
    private JComponent buildRow(StudentQuery q) {
        InventoryPanel.RowPanel row = new InventoryPanel.RowPanel(InventoryPanel.ROW_H, true, MINW, WEIGHT, ALIGN, 2);

        JCheckBox cb = new JCheckBox();
        cb.setOpaque(false);
        checks.put(q, cb);
        row.add(cb);

        JLabel ticket = new JLabel("#T-" + q.getTicketNumber());
        ticket.setFont(new Font("SansSerif", Font.BOLD, 12));
        ticket.setForeground(InventoryPanel.INK);
        row.add(ticket);

        JLabel name = new JLabel(q.getStudentName());
        name.setFont(new Font("SansSerif", Font.BOLD, 12));
        name.setForeground(InventoryPanel.INK);
        row.add(name);

        JLabel subj = new JLabel(UIKit.displayCode(q.getSubjectCode()));
        subj.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subj.setForeground(InventoryPanel.SLATE);
        row.add(subj);

        JLabel cat = new JLabel(q.getCategory());
        cat.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cat.setForeground(new Color(40, 100, 200));
        row.add(cat);

        row.add(priorityBadge(q.getPriority()));

        JLabel sub = new JLabel(q.getSubmittedAt().format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)));
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(InventoryPanel.SEMESTER_TXT);
        row.add(sub);

        row.add(statusBadge(q.getStatus()));

        UIKit.RoundButton view = new UIKit.RoundButton("View", new Color(225, 225, 225), new Color(210, 210, 210), UIKit.TEXT_DARK, 6, 4, 10);
        view.addActionListener(e -> openTicket(q));
        row.add(view);

        return row;
    }

    /** The colored Low / Medium / High badge. */
    private static InventoryPanel.StatusBadge priorityBadge(QueryPriority p) {
        switch (p) {
            case HIGH: return new InventoryPanel.StatusBadge("High", new Color(254, 226, 226), new Color(153, 27, 27), new Color(239, 68, 68));
            case MEDIUM: return new InventoryPanel.StatusBadge("Medium", new Color(254, 243, 199), new Color(133, 50, 10), new Color(245, 158, 11));
            case LOW:
            default: return new InventoryPanel.StatusBadge("Low", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129));
        }
    }

    /** The colored status badge of a ticket. */
    private static InventoryPanel.StatusBadge statusBadge(QueryStatus s) {
        switch (s) {
            case ANSWERED: return new InventoryPanel.StatusBadge("Answered", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129));
            case ESCALATED: return new InventoryPanel.StatusBadge("Escalated", new Color(254, 226, 226), new Color(153, 27, 27), new Color(239, 68, 68));
            case PENDING:
            default: return new InventoryPanel.StatusBadge("Pending", new Color(254, 243, 199), new Color(133, 50, 10), new Color(245, 158, 11));
        }
    }

    /** Readable name of a ticket status. */
    private static String statusName(QueryStatus s) {
        switch (s) {
            case ANSWERED: return "Answered";
            case ESCALATED: return "Escalated";
            default: return "Pending";
        }
    }

    /**
     * Opens the ticket window to read, answer or escalate it, then saves the result and notifies the
     * student.
     */
    private void openTicket(StudentQuery q) {
        QueryStatus before = q.getStatus();
        TicketDialog dlg = new TicketDialog(SwingUtilities.getWindowAncestor(this), q);
        dlg.setVisible(true);
        if (dlg.isChanged()) {
            store.saveQuery(q);
            if (q.getStatus() == QueryStatus.ANSWERED && before != QueryStatus.ANSWERED) {
                store.addNotification(new NotificationItem(q.getStudentId(), "Query Answered \u2014 Ticket #T-" + q.getTicketNumber(),
                        "Staff replied to your \"" + q.getCategory() + "\" query: " + q.getReply()));
            }
            if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
            refresh();
        }
    }

    /** Re-reads the tickets and rebuilds the stat cards and the table. */
    public void refresh() {
        // Staff cannot archive or delete tickets, so every ticket is listed.
        List<StudentQuery> inView = new ArrayList<>(store.getQueries());
        long open = inView.stream().filter(q -> q.getStatus() != QueryStatus.ANSWERED).count();
        long answeredToday = inView.stream().filter(q -> q.getStatus() == QueryStatus.ANSWERED
                && q.getAnsweredAt() != null && q.getAnsweredAt().toLocalDate().equals(LocalDate.now())).count();
        long escalated = inView.stream().filter(q -> q.getStatus() == QueryStatus.ESCALATED).count();

        statsRow.removeAll();
        statsRow.add(InventoryPanel.statCard("Open Tickets", open + " Tickets", InventoryPanel.DELETE_RED));
        statsRow.add(InventoryPanel.statCard("Answered Today", answeredToday + " Resolved", InventoryPanel.STAT_READY));
        statsRow.add(InventoryPanel.statCard("Escalated", escalated + " Critical", new Color(180, 120, 20)));
        statsRow.add(InventoryPanel.statCard("Total Tickets", inView.size() + " Tickets", InventoryPanel.STAT_TOTAL));
        statsRow.revalidate();
        statsRow.repaint();

        String query = searchBox.field.getText().trim().toLowerCase();
        String statusSel = statusFilter.getSelected();

        List<StudentQuery> sorted = new ArrayList<>(inView);
        sorted.sort((x, y) -> {
            boolean xe = x.getStatus() == QueryStatus.ESCALATED, ye = y.getStatus() == QueryStatus.ESCALATED;
            if (xe != ye) return xe ? -1 : 1;
            return y.getSubmittedAt().compareTo(x.getSubmittedAt());
        });

        checks.clear();
        tableBody.removeAll();
        tableBody.add(buildHeaderRow());
        selectAll.setSelected(false);
        boolean any = false;
        for (StudentQuery q : sorted) {
            if (!"All".equals(statusSel) && !statusSel.equals(statusName(q.getStatus()))) continue;
            if (!query.isEmpty()) {
                String hay = (q.getStudentName() + " #T-" + q.getTicketNumber() + " " + q.getSubjectCode() + " "
                        + UIKit.displayCode(q.getSubjectCode()) + " " + q.getCategory()).toLowerCase();
                if (!hay.contains(query)) continue;
            }
            tableBody.add(buildRow(q));
            any = true;
        }
        if (!any) {
            String msg = inView.isEmpty() ? "No student queries right now." : "No tickets match your filters.";
            JLabel empty = new JLabel(msg);
            empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
            empty.setForeground(InventoryPanel.SLATE);
            empty.setBorder(new EmptyBorder(16, InventoryPanel.PAD_LEFT, 16, 0));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            tableBody.add(empty);
        }
        tableBody.revalidate();
        tableBody.repaint();
    }

    /** Modal to read the full ticket and send a reply / change its status. */
    static class TicketDialog extends JDialog {
        /** True when the staff member answered or escalated the ticket. */
        private boolean changed = false;

        TicketDialog(Window owner, StudentQuery q) {
            super(owner, "Ticket #T-" + q.getTicketNumber(), ModalityType.APPLICATION_MODAL);
            setSize(460, 420);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());

            JPanel content = new JPanel();
            content.setBorder(new EmptyBorder(16, 16, 16, 16));
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            JLabel who = new JLabel(q.getStudentName() + " \u2014 " + q.getCategory());
            who.setFont(new Font("SansSerif", Font.BOLD, 14));
            content.add(who);
            content.add(Box.createVerticalStrut(6));

            JTextArea msg = new JTextArea(q.getMessage());
            msg.setEditable(false);
            msg.setLineWrap(true);
            msg.setWrapStyleWord(true);
            msg.setOpaque(false);
            content.add(msg);
            content.add(Box.createVerticalStrut(14));

            content.add(new JLabel("Reply"));
            JTextArea reply = new JTextArea(q.getReply(), 5, 30);
            reply.setLineWrap(true);
            reply.setWrapStyleWord(true);
            JScrollPane replyScroll = new JScrollPane(reply);
            content.add(replyScroll);
            content.add(Box.createVerticalStrut(10));

            content.add(new JLabel("Status"));
            JComboBox<QueryStatus> statusCombo = new JComboBox<>(QueryStatus.values());
            statusCombo.setSelectedItem(q.getStatus());
            content.add(statusCombo);

            add(content, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton close = new JButton("Close");
            JButton save = new JButton("Save");
            buttons.add(close);
            buttons.add(save);
            add(buttons, BorderLayout.SOUTH);

            close.addActionListener(e -> dispose());
            save.addActionListener(e -> {
                q.setReply(reply.getText().trim());
                q.setStatus((QueryStatus) statusCombo.getSelectedItem());
                changed = true;
                dispose();
            });
        }

        /** True when the ticket was changed (answered or escalated). */
        boolean isChanged() { return changed; }
    }
}
