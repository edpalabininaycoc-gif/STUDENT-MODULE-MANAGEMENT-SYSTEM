package moduledistributionsystem.ui.student;

import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.model.NotificationItem;
import moduledistributionsystem.model.QueryPriority;
import moduledistributionsystem.model.QueryStatus;
import moduledistributionsystem.model.Student;
import moduledistributionsystem.model.StudentQuery;

/**
 * Student: submit a question/concern to staff, and track replies (feeds Staff's Inquiries Desk).
 * Layout follows the "student-queries" Figma frame: one white card holding the category
 * and urgency chips, a gray band with the optional module + message, the Submit button, and the
 * student's tickets. Colors follow the student Overview (black ink, neutral grays, white page). The related module is optional: the first choice is "None".
 */
public class StudentQueriesPanel extends JPanel {
    // ---------- Palette (matches the student Overview: black outline/ink, neutral grays, white page) ----------
    private static final Color PAGE_BG      = Color.WHITE;
    private static final Color INK          = new Color(25, 25, 25);     // Overview "CARD_LINE" black
    private static final Color INK_HOVER    = new Color(60, 60, 60);
    private static final Color SLATE        = new Color(85, 85, 85);
    private static final Color LABEL        = new Color(120, 122, 118);  // UIKit.TEXT_MUTED
    private static final Color BAND_LABEL   = new Color(60, 60, 60);
    private static final Color CHIP_TEXT    = new Color(55, 55, 55);
    private static final Color CHIP_BORDER  = new Color(214, 214, 214);
    private static final Color CARD_BORDER  = INK;                       // black outline, like the Overview cards
    private static final Color BAND_BG      = new Color(227, 227, 227);
    private static final Color TICKET_BG    = new Color(242, 242, 242);  // Overview "CARD_FILL"
    private static final Color DIVIDER      = new Color(232, 232, 232);
    private static final Color HINT_BLUE    = new Color(30, 115, 240);
    private static final Color MUTED        = new Color(140, 140, 140);

    /** How many module rows the drop-down list shows before it starts to scroll. */
    private static final int DROPDOWN_VISIBLE_ROWS = 6;

    /** First entry of the module drop-down: the module is optional. */
    private static final String NONE_LABEL = "None (no specific module)";
    /** How many tickets the card lists; "View History" shows all of them. */
    private static final int RECENT_LIMIT = 5;

    private static final String[] CATEGORIES = {"Module Availability", "General Inquiry", "Schedule Conflict", "Booking Issue", "Other"};
    private static final QueryPriority[] PRIORITIES = {QueryPriority.LOW, QueryPriority.MEDIUM, QueryPriority.HIGH};
    private static final String[] PRIORITY_LABELS = {"Low", "Medium", "High"};

    private final DemoDataStore store;
    private final StudentMainFrame parent;

    private OutlineChip[] categoryChips;
    private final ButtonGroup categoryGroup = new ButtonGroup();
    private OutlineChip[] priorityChips;
    private final ButtonGroup priorityGroup = new ButtonGroup();

    private ModuleDropdown moduleDropdown;
    private JTextArea messageArea;
    private JLabel charHint;

    private JPanel ticketsHolder;
    private JLabel ticketsHeading;
    private Chevron ticketsChevron;
    private boolean ticketsExpanded = true;

    public StudentQueriesPanel(DemoDataStore store, StudentMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(PAGE_BG);
        setBorder(new EmptyBorder(26, 32, 28, 32));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Student Queries");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(INK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Submit questions or report issues directly to your module instructors.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(SLATE);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(18));
        add(header, BorderLayout.NORTH);

        JPanel content = new UIKit.ScrollableBoxPanel();
        content.setBorder(new EmptyBorder(0, 0, 0, 6));

        CardPanel card = new CardPanel();
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(buildCategorySection());
        card.add(buildDetailsBand());
        card.add(buildSubmitArea());
        card.add(divider());
        card.add(buildTicketsSection());
        content.add(card);

        add(UIKit.scroll(content), BorderLayout.CENTER);

        refresh();
    }

    // ------------------------------------------------------------------ sections

    private JComponent buildCategorySection() {
        Section s = new Section();
        s.setBorder(new EmptyBorder(16, 23, 16, 23));

        s.add(sectionLabel("What is your query about?", LABEL));
        s.add(Box.createVerticalStrut(12));

        JPanel chips = chipRow();
        categoryChips = new OutlineChip[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) {
            OutlineChip chip = new OutlineChip(CATEGORIES[i], 26, 12, 15, CHIP_TEXT);
            categoryGroup.add(chip);
            categoryChips[i] = chip;
            if (i > 0) chips.add(Box.createHorizontalStrut(6));
            chips.add(chip);
        }
        chips.add(Box.createHorizontalGlue());
        categoryChips[1].setSelected(true); // default to "General Inquiry"
        s.add(chips);
        s.add(Box.createVerticalStrut(16));

        s.add(sectionLabel("How urgent is this?", LABEL));
        s.add(Box.createVerticalStrut(11));

        JPanel prio = chipRow();
        priorityChips = new OutlineChip[PRIORITY_LABELS.length];
        for (int i = 0; i < PRIORITY_LABELS.length; i++) {
            OutlineChip chip = new OutlineChip(PRIORITY_LABELS[i], 30, 13, 20, INK);
            priorityGroup.add(chip);
            priorityChips[i] = chip;
            if (i > 0) prio.add(Box.createHorizontalStrut(6));
            prio.add(chip);
        }
        prio.add(Box.createHorizontalGlue());
        priorityChips[0].setSelected(true); // default to Low
        s.add(prio);
        return s;
    }

    private JComponent buildDetailsBand() {
        Section s = new Section();
        s.setOpaque(true);
        s.setBackground(BAND_BG);
        s.setBorder(new EmptyBorder(14, 23, 16, 23));

        s.add(sectionLabel("Related module (optional)", BAND_LABEL));
        s.add(Box.createVerticalStrut(8));
        moduleDropdown = new ModuleDropdown();
        moduleDropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(moduleDropdown);
        s.add(Box.createVerticalStrut(12));

        s.add(sectionLabel("Your message", BAND_LABEL));
        s.add(Box.createVerticalStrut(8));

        messageArea = new JTextArea(5, 40);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        messageArea.setForeground(SLATE);
        messageArea.setCaretColor(INK);
        messageArea.setBackground(Color.WHITE);
        messageArea.setBorder(null);
        JScrollPane messageScroll = new JScrollPane(messageArea);
        messageScroll.setBorder(null);
        messageScroll.setOpaque(false);
        messageScroll.getViewport().setOpaque(true);
        messageScroll.getViewport().setBackground(Color.WHITE);

        RoundedBox box = new RoundedBox(8, Color.WHITE);
        box.setLayout(new BorderLayout());
        box.setBorder(new EmptyBorder(10, 12, 10, 12));
        box.add(messageScroll, BorderLayout.CENTER);
        box.setPreferredSize(new Dimension(10, 104));
        box.setMinimumSize(new Dimension(10, 104));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(box);
        s.add(Box.createVerticalStrut(6));

        charHint = new JLabel("Minimum 10 characters");
        charHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        charHint.setForeground(HINT_BLUE);
        charHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(charHint);

        messageArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateHint(); }
            public void removeUpdate(DocumentEvent e) { updateHint(); }
            public void changedUpdate(DocumentEvent e) { updateHint(); }
        });
        return s;
    }

    private JComponent buildSubmitArea() {
        Section s = new Section();
        s.setBorder(new EmptyBorder(13, 23, 17, 23));
        UIKit.RoundButton submit = new UIKit.RoundButton("Submit Query", INK, INK_HOVER, Color.WHITE, 8);
        submit.setFont(new Font("SansSerif", Font.BOLD, 13));
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setPreferredSize(new Dimension(10, 36));
        submit.setMinimumSize(new Dimension(10, 36));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        submit.addActionListener(e -> submitQuery());
        s.add(submit);
        return s;
    }

    private JComponent divider() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(DIVIDER);
        line.setPreferredSize(new Dimension(10, 1));
        line.setMinimumSize(new Dimension(10, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    private JComponent buildTicketsSection() {
        Section s = new Section();
        s.setBorder(new EmptyBorder(17, 23, 20, 23));

        JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setOpaque(false);
        headingRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        ticketsHeading = new JLabel("My tickets (0)");
        ticketsHeading.setFont(new Font("SansSerif", Font.BOLD, 13));
        ticketsHeading.setForeground(INK);
        ticketsChevron = new Chevron();
        left.add(ticketsHeading);
        left.add(Box.createHorizontalStrut(8));
        left.add(ticketsChevron);
        MouseAdapter toggle = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ticketsExpanded = !ticketsExpanded;
                refresh();
            }
        };
        left.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        left.addMouseListener(toggle);
        ticketsHeading.addMouseListener(toggle);
        ticketsChevron.addMouseListener(toggle);
        headingRow.add(left, BorderLayout.WEST);

        JLabel history = new JLabel("<html><u>View History</u></html>");
        history.setFont(new Font("SansSerif", Font.BOLD, 11));
        history.setForeground(SLATE);
        history.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        history.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { showHistory(); }
        });
        headingRow.add(history, BorderLayout.EAST);
        headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, headingRow.getPreferredSize().height));

        s.add(headingRow);
        s.add(Box.createVerticalStrut(12));

        ticketsHolder = new JPanel();
        ticketsHolder.setLayout(new BoxLayout(ticketsHolder, BoxLayout.Y_AXIS));
        ticketsHolder.setOpaque(false);
        ticketsHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(ticketsHolder);
        return s;
    }

    private JLabel sectionLabel(String text, Color color) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JPanel chipRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private void updateHint() {
        int len = messageArea.getText().trim().length();
        if (len >= 10) {
            charHint.setText(len + " characters");
            charHint.setForeground(new Color(22, 130, 70));
        } else {
            charHint.setText("Minimum 10 characters (" + len + "/10)");
            charHint.setForeground(HINT_BLUE);
        }
    }

    // ------------------------------------------------------------------ tickets

    private List<StudentQuery> myTickets() {
        Student current = store.getCurrentStudent();
        List<StudentQuery> mine = new ArrayList<>();
        if (current != null) {
            for (StudentQuery q : store.getQueries()) {
                if (current.getId().equals(q.getStudentId())) mine.add(q);
            }
        }
        Collections.reverse(mine); // most recent first
        return mine;
    }

    private JPanel ticketCard(StudentQuery q) {
        UIKit.RoundPanel card = new UIKit.RoundPanel(12, TICKET_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel ticket = new JLabel("#T-" + q.getTicketNumber() + " - " + q.getCategory());
        ticket.setFont(new Font("SansSerif", Font.BOLD, 12));
        ticket.setForeground(INK);
        top.add(ticket, BorderLayout.WEST);
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pills.setOpaque(false);
        pills.add(priorityPill(q.getPriority()));
        pills.add(statusPill(q.getStatus()));
        top.add(pills, BorderLayout.EAST);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
        card.add(top);
        card.add(Box.createVerticalStrut(8));

        JLabel msg = new JLabel("<html><div style='width:560px'>" + escape(q.getMessage()) + "</div></html>");
        msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        msg.setForeground(SLATE);
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(msg);

        if (!q.getReply().isEmpty()) {
            card.add(Box.createVerticalStrut(6));
            JLabel reply = new JLabel("<html><div style='width:560px'><b>Staff reply:</b> " + escape(q.getReply()) + "</div></html>");
            reply.setFont(new Font("SansSerif", Font.PLAIN, 12));
            reply.setForeground(new Color(6, 95, 70));
            reply.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(reply);
        }
        card.add(Box.createVerticalStrut(9));

        JPanel when = new JPanel();
        when.setLayout(new BoxLayout(when, BoxLayout.X_AXIS));
        when.setOpaque(false);
        when.setAlignmentX(Component.LEFT_ALIGNMENT);
        when.add(new ClockIcon());
        when.add(Box.createHorizontalStrut(5));
        JLabel date = new JLabel(friendlyTime(q.getSubmittedAt()));
        date.setFont(new Font("SansSerif", Font.PLAIN, 11));
        date.setForeground(MUTED);
        when.add(date);
        when.setMaximumSize(new Dimension(Integer.MAX_VALUE, when.getPreferredSize().height));
        card.add(when);
        return card;
    }

    /** "Today, 10:24 AM" / "Yesterday, 3:15 PM" / "Sep 18, 3:15 PM" (year added for older tickets). */
    private static String friendlyTime(LocalDateTime t) {
        String time = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        LocalDate today = LocalDate.now();
        if (t.toLocalDate().equals(today)) return "Today, " + time;
        if (t.toLocalDate().equals(today.minusDays(1))) return "Yesterday, " + time;
        String day = t.format(DateTimeFormatter.ofPattern(t.getYear() == today.getYear() ? "MMM d" : "MMM d, yyyy", Locale.ENGLISH));
        return day + ", " + time;
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private TagPill statusPill(QueryStatus s) {
        switch (s) {
            case ANSWERED:  return new TagPill("Answered", new Color(219, 234, 254), new Color(29, 78, 216));
            case ESCALATED: return new TagPill("Escalated", new Color(237, 233, 254), new Color(91, 33, 182));
            case PENDING:
            default:        return new TagPill("Pending", new Color(209, 250, 229), new Color(6, 95, 70));
        }
    }

    private TagPill priorityPill(QueryPriority p) {
        switch (p) {
            case HIGH:   return new TagPill("High", new Color(254, 226, 226), new Color(220, 38, 38));
            case MEDIUM: return new TagPill("Medium", new Color(254, 243, 199), new Color(217, 119, 6));
            case LOW:
            default:     return new TagPill("Low", new Color(226, 232, 240), new Color(71, 85, 105));
        }
    }

    private void showHistory() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Query History", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        JPanel list = new UIKit.ScrollableBoxPanel();
        list.setOpaque(true);
        list.setBackground(Color.WHITE);
        list.setBorder(new EmptyBorder(16, 16, 16, 16));
        List<StudentQuery> mine = myTickets();
        if (mine.isEmpty()) {
            JLabel empty = new JLabel("You haven't submitted any queries yet.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 12));
            empty.setForeground(SLATE);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(empty);
        }
        boolean first = true;
        for (StudentQuery q : mine) {
            if (!first) list.add(Box.createVerticalStrut(10));
            list.add(ticketCard(q));
            first = false;
        }
        dialog.add(UIKit.scroll(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);

        dialog.setSize(680, 560);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------------ actions

    private void submitQuery() {
        Student current = store.getCurrentStudent();
        if (current == null) {
            JOptionPane.showMessageDialog(this, "You need to be logged in as a student to submit a query.", "Student Queries", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String category = null;
        for (int i = 0; i < categoryChips.length; i++) {
            if (categoryChips[i].isSelected()) { category = CATEGORIES[i]; break; }
        }
        if (category == null) {
            JOptionPane.showMessageDialog(this, "Please choose what your query is about.", "Student Queries", JOptionPane.WARNING_MESSAGE);
            return;
        }
        QueryPriority priority = QueryPriority.LOW;
        for (int i = 0; i < priorityChips.length; i++) {
            if (priorityChips[i].isSelected()) { priority = PRIORITIES[i]; break; }
        }
        String message = messageArea.getText().trim();
        if (message.length() < 10) {
            JOptionPane.showMessageDialog(this, "Please describe your query in at least 10 characters.", "Student Queries", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // "None" means the query isn't about one particular module.
        Module selected = moduleDropdown.getSelectedModule();
        String subjectCode = selected != null ? selected.getCode() : "GENERAL";

        StudentQuery q = new StudentQuery(store.nextTicketNumber(), current.getId(), current.getName(),
                subjectCode, category, priority, message);
        store.addQuery(q);
        store.addNotification(new NotificationItem(current.getId(), "Query Submitted \u2014 Ticket #T-" + q.getTicketNumber(),
                "Your \"" + category + "\" query has been sent to staff. We'll notify you when it's answered."));

        JOptionPane.showMessageDialog(this, "Your query was submitted as Ticket #T-" + q.getTicketNumber() + ".", "Submitted", JOptionPane.INFORMATION_MESSAGE);

        categoryGroup.clearSelection();
        categoryChips[1].setSelected(true);
        priorityGroup.clearSelection();
        priorityChips[0].setSelected(true);
        moduleDropdown.selectNone();
        messageArea.setText("");
        updateHint();
        refresh();
        if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
    }

    public void refresh() {
        moduleDropdown.setModules(store.getModules());

        List<StudentQuery> mine = myTickets();
        ticketsHeading.setText("My tickets (" + mine.size() + ")");
        ticketsChevron.setExpanded(ticketsExpanded);

        ticketsHolder.removeAll();
        if (ticketsExpanded) {
            if (mine.isEmpty()) {
                JLabel empty = new JLabel("You haven't submitted any queries yet.");
                empty.setFont(new Font("SansSerif", Font.PLAIN, 12));
                empty.setForeground(SLATE);
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                ticketsHolder.add(empty);
            } else {
                int shown = 0;
                for (StudentQuery q : mine) {
                    if (shown >= RECENT_LIMIT) break;
                    if (shown > 0) ticketsHolder.add(Box.createVerticalStrut(10));
                    ticketsHolder.add(ticketCard(q));
                    shown++;
                }
            }
        }
        ticketsHolder.revalidate();
        ticketsHolder.repaint();
    }

    // ------------------------------------------------------------------ building blocks

    /** Vertical stack that stretches full width but never taller than its content. */
    private static class Section extends JPanel {
        Section() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** The big white rounded card with a thin border. Children are painted first, then the border on top. */
    private static class CardPanel extends JPanel {
        CardPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.dispose();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** White rounded rectangle (the message box). */
    private static class RoundedBox extends JPanel {
        private final int radius;
        private final Color fill;

        RoundedBox(int radius, Color fill) {
            this.radius = radius;
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Pill-shaped choice: white with a thin border, or dark navy when selected. */
    private static class OutlineChip extends JToggleButton {
        private final int height;
        private final int padding;
        private final Color textColor;
        private boolean hover = false;

        OutlineChip(String text, int height, int fontSize, int padding, Color textColor) {
            super(text);
            this.height = height;
            this.padding = padding;
            this.textColor = textColor;
            setFont(new Font("SansSerif", Font.PLAIN, fontSize));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + padding * 2, height);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (isSelected()) {
                g2.setColor(INK);
                g2.fillRoundRect(0, 0, w, h, h, h);
            } else {
                g2.setColor(hover ? TICKET_BG : Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
                g2.setColor(CHIP_BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            }
            g2.setFont(getFont());
            g2.setColor(isSelected() ? Color.WHITE : textColor);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /**
     * Dark navy drop-down for the optional module. The first entry is "None"; the rest are the modules.
     * Click to open the list.
     */
    private static class ModuleDropdown extends JComponent {
        private final List<Module> modules = new ArrayList<>();
        private Module selected = null; // null = "None"
        private boolean hover = false;
        /** When the popup last closed -- used so the click that closes it doesn't instantly reopen it. */
        private long lastHidden = 0;

        ModuleDropdown() {
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    // Swing closes an open popup on any outside press -- including a press on this very
                    // button. If it just closed, treat this click as the "close" click and don't reopen.
                    if (System.currentTimeMillis() - lastHidden < 250) return;
                    showPopup();
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        Module getSelectedModule() { return selected; }

        void selectNone() {
            selected = null;
            repaint();
        }

        /** Reload the list of modules, keeping the current pick if that module still exists. */
        void setModules(List<Module> list) {
            UUID keep = selected != null ? selected.getId() : null;
            modules.clear();
            modules.addAll(list);
            selected = null;
            if (keep != null) {
                for (Module m : modules) if (m.getId().equals(keep)) selected = m;
            }
            repaint();
        }

        private static String label(Module m) { return m == null ? NONE_LABEL : m.getCode() + " - " + m.getTitle(); }

        /**
         * Opens the list as a short scrollable popup (about DROPDOWN_VISIBLE_ROWS rows tall) instead of
         * one tall menu holding every module. Mouse wheel / scroll bar / arrow keys move through it.
         */
        private void showPopup() {
            final int rowH = 30;
            final DefaultListModel<String> model = new DefaultListModel<>();
            model.addElement(NONE_LABEL);
            for (Module m : modules) model.addElement(label(m));

            final JList<String> list = new JList<>(model);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setFixedCellHeight(rowH);
            list.setBackground(Color.WHITE);
            list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            int current = selected == null ? 0 : modules.indexOf(selected) + 1;
            list.setSelectedIndex(Math.max(current, 0));
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                              boolean isSelected, boolean focus) {
                    JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, false, false);
                    c.setOpaque(true);
                    c.setBorder(new EmptyBorder(0, 14, 0, 14));
                    c.setFont(new Font("SansSerif", index == (selected == null ? 0 : modules.indexOf(selected) + 1)
                            ? Font.BOLD : Font.PLAIN, 12));
                    c.setBackground(isSelected ? INK : Color.WHITE);
                    c.setForeground(isSelected ? Color.WHITE : INK);
                    c.setToolTipText(String.valueOf(value));
                    return c;
                }
            });

            final JPopupMenu menu = new JPopupMenu();
            menu.setBorder(BorderFactory.createLineBorder(INK, 1));
            menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { }
                @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { lastHidden = System.currentTimeMillis(); }
                @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { lastHidden = System.currentTimeMillis(); }
            });

            // Hover highlights a row; click or Enter picks it and closes the popup.
            list.addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) list.setSelectedIndex(i);
                }
            });
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) choose(i, menu);
                }
            });
            list.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && list.getSelectedIndex() >= 0) {
                        choose(list.getSelectedIndex(), menu);
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                        menu.setVisible(false);
                    }
                }
            });

            JScrollPane sp = UIKit.scroll(list);
            sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(Color.WHITE);
            int rows = Math.min(model.getSize(), DROPDOWN_VISIBLE_ROWS);
            sp.setPreferredSize(new Dimension(getWidth() - 2, rows * rowH));
            menu.add(sp);
            menu.show(this, 0, getHeight() + 2);
            list.ensureIndexIsVisible(list.getSelectedIndex());
            list.requestFocusInWindow();
        }

        private void choose(int index, JPopupMenu menu) {
            selected = index <= 0 ? null : modules.get(index - 1);
            menu.setVisible(false);
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(10, 34); }
        @Override public Dimension getMinimumSize() { return new Dimension(10, 34); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 34); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(hover ? INK_HOVER : INK);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setFont(getFont());
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            String text = label(selected);
            int maxW = getWidth() - 14 - 40;
            while (fm.stringWidth(text) > maxW && text.length() > 4) text = text.substring(0, text.length() - 2);
            if (!text.equals(label(selected))) text = text.trim() + "...";
            g2.drawString(text, 14, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            // chevron
            float cx = getWidth() - 20f, cy = getHeight() / 2f;
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D chevron = new Path2D.Float();
            chevron.moveTo(cx - 4, cy - 2);
            chevron.lineTo(cx, cy + 2);
            chevron.lineTo(cx + 4, cy - 2);
            g2.draw(chevron);
            g2.dispose();
        }
    }

    /** Small uppercase status/priority tag, like HIGH / PENDING in the Figma. */
    private static class TagPill extends JComponent {
        private final String text;
        private final Color bg, fg;

        TagPill(String text, Color bg, Color fg) {
            this.text = text.toUpperCase();
            this.bg = bg;
            this.fg = fg;
            setFont(new Font("SansSerif", Font.BOLD, 9));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + 16, 18);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(fg);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Small clock outline in front of the ticket time. */
    private static class ClockIcon extends JComponent {
        ClockIcon() { setOpaque(false); }

        @Override public Dimension getPreferredSize() { return new Dimension(12, 12); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(MUTED);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(1, 1, 10, 10));
            g2.drawLine(6, 3, 6, 6);
            g2.drawLine(6, 6, 8, 7);
            g2.dispose();
        }
    }

    /** Small chevron next to "My tickets": points down when the list is open, right when it is closed. */
    private static class Chevron extends JComponent {
        private boolean expanded = true;

        Chevron() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setExpanded(boolean expanded) {
            this.expanded = expanded;
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(10, 10); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(SLATE);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Float();
            if (expanded) {
                p.moveTo(1.5, 3.5);
                p.lineTo(5, 7);
                p.lineTo(8.5, 3.5);
            } else {
                p.moveTo(3.5, 1.5);
                p.lineTo(7, 5);
                p.lineTo(3.5, 8.5);
            }
            g2.draw(p);
            g2.dispose();
        }
    }
}