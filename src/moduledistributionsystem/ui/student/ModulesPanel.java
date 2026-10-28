package moduledistributionsystem.ui.student;

import moduledistributionsystem.ui.student.StudentMainFrame;
import moduledistributionsystem.ui.common.UIKit;
import moduledistributionsystem.ui.staff.InventoryPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.model.NotificationItem;

/** Modules list and booking panel -- shows only the modules the current student is enrolled in. */
public class ModulesPanel extends JPanel {
    private static final Color SOON_ICON_BG = new Color(219, 234, 248);
    private static final Color SOON_ICON_FG = new Color(30, 80, 140);
    // Card look copied from the student Overview "At a glance" cards: light gray fill + thin black outline.
    private static final Color CARD_FILL = new Color(242, 242, 242);
    private static final Color CARD_LINE = new Color(25, 25, 25);
    private static final Color READY_TEXT   = new Color(20, 120, 60);
    private static final Color SOON_TEXT    = new Color(178, 74, 22);

    private DemoDataStore store;
    private StudentMainFrame parent;
    private JTextField searchField;
    private String filterMode = "All";
    private JPanel listHolder;
    private JLabel totalStat, readyStat, soonStat;

    public ModulesPanel(DemoDataStore store, StudentMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("Academic Modules");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Modules ready or scheduled for distribution this semester");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(UIKit.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(18));

        // ---- Stat row: Total / Ready / Coming Soon, scoped to this student's enrolled modules ----
        JPanel statRow = new JPanel(new GridLayout(1, 3, 14, 0));
        statRow.setOpaque(false);
        statRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));

        totalStat = new JLabel();
        readyStat = new JLabel();
        soonStat = new JLabel();
        statRow.add(statTile(StatIcon.Kind.BOOK, UIKit.GREEN_PILL_BG, READY_TEXT, totalStat, UIKit.TEXT_DARK, "Enrolled Course Modules"));
        statRow.add(statTile(StatIcon.Kind.CLIPBOARD, UIKit.AMBER_PILL_BG, UIKit.AMBER_PILL_FG, readyStat, READY_TEXT, "Available for Collection"));
        statRow.add(statTile(StatIcon.Kind.CLOCK, SOON_ICON_BG, SOON_ICON_FG, soonStat, SOON_TEXT, "Coming Soon"));

        header.add(statRow);
        header.add(Box.createVerticalStrut(16));

        JPanel toolRow = new JPanel(new BorderLayout(14, 0));
        toolRow.setOpaque(false);
        toolRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        // Same outlined search box the admin/staff screens use (InventoryPanel.SearchBox).
        InventoryPanel.SearchBox searchBox = new InventoryPanel.SearchBox("Search module code or title...", 300);
        searchBox.setPreferredSize(new Dimension(300, 34));
        searchField = searchBox.field;
        JPanel searchHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchHolder.setOpaque(false);
        searchHolder.add(searchBox);
        toolRow.add(searchHolder, BorderLayout.WEST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        ButtonGroup filterGroup = new ButtonGroup();
        UIKit.ToggleChip allChip = new UIKit.ToggleChip("All", 8);
        UIKit.ToggleChip availChip = new UIKit.ToggleChip("Available", 8);
        UIKit.ToggleChip outChip = new UIKit.ToggleChip("Out of Stock", 8);
        UIKit.ToggleChip soonChip = new UIKit.ToggleChip("Coming Soon", 8);
        for (UIKit.ToggleChip c : new UIKit.ToggleChip[]{allChip, availChip, outChip, soonChip}) {
            filterGroup.add(c);
        }
        allChip.setSelected(true);
        allChip.addActionListener(e -> { filterMode = "All"; refresh(); });
        availChip.addActionListener(e -> { filterMode = "Available"; refresh(); });
        outChip.addActionListener(e -> { filterMode = "Out of Stock"; refresh(); });
        soonChip.addActionListener(e -> { filterMode = "Coming Soon"; refresh(); });
        filters.add(allChip);
        filters.add(availChip);
        filters.add(outChip);
        filters.add(soonChip);
        toolRow.add(filters, BorderLayout.EAST);

        header.add(toolRow);
        add(header, BorderLayout.NORTH);

        listHolder = new UIKit.ScrollableBoxPanel();
        listHolder.setBorder(new EmptyBorder(18, 0, 0, 6));
        add(UIKit.scroll(listHolder), BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });

        refresh();
    }

    /** One "Total / Ready / Coming Soon" tile: a small colored icon square beside a big number + caption. */
    private JPanel statTile(StatIcon.Kind kind, Color iconBg, Color iconFg, JLabel bigLabel, Color bigColor, String caption) {
        OutlinedCard card = new OutlinedCard(14);
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        card.add(UIKit.westWrap(new StatIcon(kind, iconBg, iconFg)), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        bigLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        bigLabel.setForeground(bigColor);
        bigLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        captionLabel.setForeground(UIKit.TEXT_MUTED);
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        text.add(bigLabel);
        text.add(captionLabel);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel moduleCard(Module m) {
        OutlinedCard card = new OutlinedCard(18);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setCursor(Cursor.getPredefinedCursor(m.isAvailable() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topLeft.setOpaque(false);
        JLabel code = new JLabel(UIKit.displayCode(m.getCode()));
        code.setFont(new Font("SansSerif", Font.BOLD, 12));
        code.setForeground(READY_TEXT);
        JLabel credits = new JLabel(m.getCredits() + " Credits");
        credits.setFont(new Font("SansSerif", Font.PLAIN, 11));
        credits.setForeground(UIKit.TEXT_MUTED);
        topLeft.add(code);
        topLeft.add(credits);
        topRow.add(topLeft, BorderLayout.WEST);

        UIKit.Pill status;
        if (m.isAvailable() && m.getStocks() <= 0) {
            status = new UIKit.Pill("Out of Stock", UIKit.RED_PILL_BG, UIKit.RED_PILL_FG);
        } else if (m.isAvailable()) {
            status = new UIKit.Pill("Available", UIKit.GREEN_PILL_BG, READY_TEXT);
        } else {
            status = new UIKit.Pill("Coming Soon", UIKit.AMBER_PILL_BG, UIKit.AMBER_PILL_FG);
        }
        topRow.add(UIKit.westWrap(status), BorderLayout.EAST);

        JLabel titleLabel = new JLabel(m.getTitle());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLabel.setForeground(UIKit.TEXT_DARK);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setBorder(new EmptyBorder(8, 0, 4, 0));

        JLabel loc = new JLabel("\uD83D\uDCCD " + m.getLocation());
        loc.setFont(new Font("SansSerif", Font.PLAIN, 11));
        loc.setForeground(UIKit.TEXT_MUTED);
        loc.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSeparator divider = new JSeparator();
        divider.setForeground(new Color(222, 222, 222));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setBorder(new EmptyBorder(12, 0, 12, 0));

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel deadlineCol = new JPanel();
        deadlineCol.setLayout(new BoxLayout(deadlineCol, BoxLayout.Y_AXIS));
        deadlineCol.setOpaque(false);
        JLabel deadlineLabel = new JLabel("COLLECTION DEADLINE");
        deadlineLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
        deadlineLabel.setForeground(UIKit.TEXT_MUTED);
        deadlineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel deadlineValue = new JLabel("\uD83D\uDCC5 " + m.getCollectBy().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        deadlineValue.setFont(new Font("SansSerif", Font.BOLD, 12));
        deadlineValue.setForeground(UIKit.TEXT_DARK);
        deadlineValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        deadlineCol.add(deadlineLabel);
        deadlineCol.add(deadlineValue);
        footer.add(deadlineCol, BorderLayout.WEST);

        JPanel stockCol = new JPanel();
        stockCol.setLayout(new BoxLayout(stockCol, BoxLayout.Y_AXIS));
        stockCol.setOpaque(false);
        JLabel stockLabel = new JLabel("AVAILABLE STOCKS");
        stockLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
        stockLabel.setForeground(UIKit.TEXT_MUTED);
        stockLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        boolean soldOut = m.isAvailable() && m.getStocks() <= 0;
        JLabel stockValue = new JLabel(String.valueOf(m.getStocks()));
        stockValue.setFont(new Font("SansSerif", Font.BOLD, 13));
        stockValue.setForeground(soldOut ? UIKit.RED_PILL_FG : new Color(0, 158, 0));
        stockValue.setAlignmentX(Component.RIGHT_ALIGNMENT);
        stockCol.add(stockLabel);
        stockCol.add(stockValue);
        footer.add(stockCol, BorderLayout.EAST);

        body.add(topRow);
        body.add(titleLabel);
        body.add(loc);
        body.add(divider);
        body.add(footer);

        card.add(body, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { bookModule(m); }
        });
        return card;
    }

    private void bookModule(Module m) {
        store.reloadFromDatabase(); // see the latest stock / appointments from other computers
        Module fresh = store.findModule(m.getId());
        // Not open yet / out of stock / not on the course load / already booked or picked up.
        String block = store.bookingBlockReason(store.getCurrentStudent(), fresh);
        if (block != null) {
            boolean stock = fresh != null && fresh.isAvailable() && fresh.getStocks() <= 0;
            JOptionPane.showMessageDialog(this, block, stock ? "Out of Stock" : "Book Pickup", JOptionPane.WARNING_MESSAGE);
            refresh();
            return;
        }
        AppointmentDialog dlg = new AppointmentDialog(SwingUtilities.getWindowAncestor(this), store, fresh, null);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            String problem = store.bookAppointment(store.getCurrentStudent(), fresh, dlg.getSelectedDateTime());
            if (problem != null) {
                JOptionPane.showMessageDialog(this, problem, "Couldn't book", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Appointment requested. Staff will confirm shortly.\n\n"
                        + "Note: modules are first come, first served. A booking does not reserve a copy.", "Booked", JOptionPane.INFORMATION_MESSAGE);
            }
            if (parent != null) SwingUtilities.invokeLater(() -> parent.refreshAll());
            refresh();
        }
    }

    /** This student's enrolled modules only -- not the whole system-wide catalog. */
    /** This student's enrolled modules for the CURRENT semester only -- not their whole
     *  program's catalog across every semester. A module has to match both the student's year
     *  level (or be a manual exception -- see Student.isEnrolledIn) AND the semester the system
     *  is currently distributing for (see DemoDataStore.getCurrentSemester()). Without that
     *  second check, a 1st Year student would see both their 1st-and-2nd-semester subjects
     *  jumbled together at once, which isn't what "distribution this semester" means. */
    private List<Module> enrolledModules() {
        return store.getCurrentModulesFor(store.getCurrentStudent());
    }

    public void refresh() {
        List<Module> enrolled = enrolledModules();

        long total = enrolled.size();
        long ready = enrolled.stream().filter(Module::isBookable).count();   // open AND in stock
        long soon = enrolled.stream().filter(m -> !m.isAvailable()).count(); // not open yet
        totalStat.setText(String.valueOf(total));
        readyStat.setText(String.valueOf(ready));
        soonStat.setText(String.valueOf(soon));

        String query = searchField.getText().trim().toLowerCase();
        List<Module> visible = new ArrayList<>();
        for (Module m : enrolled) {
            if (filterMode.equals("Available") && !m.isBookable()) continue;
            if (filterMode.equals("Out of Stock") && !(m.isAvailable() && m.getStocks() <= 0)) continue;
            if (filterMode.equals("Coming Soon") && m.isAvailable()) continue;
            if (!query.isEmpty() && !(m.getCode().toLowerCase().replace(" ", "").contains(query.replace(" ", ""))
                    || m.getTitle().toLowerCase().contains(query))) continue;
            visible.add(m);
        }

        listHolder.removeAll();
        if (visible.isEmpty()) {
            JLabel empty = new JLabel("No modules match your search.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
            empty.setForeground(UIKit.TEXT_MUTED);
            listHolder.add(empty);
        } else {
            for (int i = 0; i < visible.size(); i += 2) {
                JPanel row = new JPanel(new GridLayout(1, 2, 16, 0)) {
                    @Override
                    public Dimension getMaximumSize() {
                        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                    }
                };
                row.setOpaque(false);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.add(moduleCard(visible.get(i)));
                if (i + 1 < visible.size()) {
                    row.add(moduleCard(visible.get(i + 1)));
                } else {
                    // GridLayout(1, 2) ignores its column count when it has a single row, so a lone
                    // last card would stretch across the whole row. An invisible spacer keeps it
                    // exactly one column wide, the same size as every other card.
                    JPanel spacer = new JPanel();
                    spacer.setOpaque(false);
                    row.add(spacer);
                }
                if (i > 0) listHolder.add(Box.createVerticalStrut(16));
                listHolder.add(row);
            }
        }
        listHolder.revalidate();
        listHolder.repaint();
    }

    /** Rounded card with a light gray fill and a black outline, same as the Overview cards. */
    private static class OutlinedCard extends UIKit.RoundPanel {
        private final int radius;

        OutlinedCard(int radius) {
            super(radius, CARD_FILL);
            this.radius = radius;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // gray fill
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD_LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
        }
    }

    /** Stat-card icon drawn with Java2D lines (no emoji): a soft tinted rounded tile with a crisp outline glyph. */
    private static class StatIcon extends JComponent {
        enum Kind { BOOK, CLIPBOARD, CLOCK }

        private final Kind kind;
        private final Color bg, fg;

        StatIcon(Kind kind, Color bg, Color fg) {
            this.kind = kind;
            this.bg = bg;
            this.fg = fg;
            setPreferredSize(new Dimension(44, 44));
            setMinimumSize(new Dimension(44, 44));
            setMaximumSize(new Dimension(44, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth(), h = getHeight();

            // tile: soft fill + thin outline in the glyph color
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 55));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14));

            float cx = w / 2f, cy = h / 2f;
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case BOOK:      drawBook(g2, cx, cy); break;
                case CLIPBOARD: drawClipboard(g2, cx, cy); break;
                default:        drawClock(g2, cx, cy); break;
            }
            g2.dispose();
        }

        /** Open book: two curved pages meeting at a spine. */
        private void drawBook(Graphics2D g2, float cx, float cy) {
            float x0 = cx - 11, x1 = cx + 11, top = cy - 8, bot = cy + 8;
            Path2D left = new Path2D.Float();
            left.moveTo(cx, top + 2);
            left.quadTo(cx - 5, top - 1, x0, top + 1);
            left.lineTo(x0, bot - 2);
            left.quadTo(cx - 5, bot - 5, cx, bot);
            left.closePath();
            Path2D right = new Path2D.Float();
            right.moveTo(cx, top + 2);
            right.quadTo(cx + 5, top - 1, x1, top + 1);
            right.lineTo(x1, bot - 2);
            right.quadTo(cx + 5, bot - 5, cx, bot);
            right.closePath();
            g2.draw(left);
            g2.draw(right);
        }

        /** Clipboard with a clip at the top and a check mark. */
        private void drawClipboard(Graphics2D g2, float cx, float cy) {
            g2.draw(new RoundRectangle2D.Float(cx - 8, cy - 8, 16, 19, 4, 4));
            g2.fill(new RoundRectangle2D.Float(cx - 4, cy - 11, 8, 5, 3, 3));
            Path2D check = new Path2D.Float();
            check.moveTo(cx - 3.5f, cy + 2);
            check.lineTo(cx - 1, cy + 4.5f);
            check.lineTo(cx + 3.5f, cy - 1);
            g2.draw(check);
        }

        /** Clock face: circle with hour and minute hands. */
        private void drawClock(Graphics2D g2, float cx, float cy) {
            g2.draw(new Ellipse2D.Float(cx - 10, cy - 10, 20, 20));
            Path2D hands = new Path2D.Float();
            hands.moveTo(cx, cy - 6);
            hands.lineTo(cx, cy);
            hands.lineTo(cx + 4, cy + 3);
            g2.draw(hands);
        }
    }
}