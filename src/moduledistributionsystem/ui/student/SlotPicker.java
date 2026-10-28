package moduledistributionsystem.ui.student;

import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.data.PickupSchedule;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "2. Select date" tiles and "3. Select time slot" chips of the Book / Reschedule tab (Figma design).
 * Every slot is checked against the module's per-slot limit: a slot that is full, or whose time already passed,
 * is greyed out and can't be picked; a slot that is nearly full says how many places are left.
 */
public class SlotPicker extends JPanel {
    /** How many date tiles fit in one row (use the arrows for more days). */
    private static final int TILES_PER_PAGE = 7;
    private static final Color BORDER = new Color(60, 60, 60);
    private static final Color BORDER_OFF = new Color(215, 215, 215);

    /** The shared data store (asked how many places are left in each slot). */
    private final DemoDataStore store;
    private final JPanel dateRow = new JPanel(new GridLayout(1, TILES_PER_PAGE, 10, 0));
    private final JLabel timeHeading = new JLabel();
    private final JPanel grid = new JPanel(new GridLayout(3, 4, 10, 10));
    private final JLabel info = new JLabel(" ");
    private final JButton prev = arrow("\u2039"), next = arrow("\u203A");
    private final List<DateTile> tiles = new ArrayList<>();
    private final List<SlotChip> chips = new ArrayList<>();
    private final ButtonGroup dateGroup = new ButtonGroup();
    private final ButtonGroup slotGroup = new ButtonGroup();
    private List<LocalDate> dates = new ArrayList<>();
    /** Which group of date tiles is shown. */
    private int page = 0;
    /** The chosen day. */
    private LocalDate selectedDate;
    /** The module being booked. */
    private Module module;
    /** The appointment being rescheduled (its own place does not count against its slot). */
    private Appointment ignore;

    /** Builds the date tiles, the time-slot chips and the hint line. */
    public SlotPicker(DemoDataStore store) {
        this.store = store;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(heading("2. SELECT DATE"));
        JPanel dateWrap = new JPanel(new BorderLayout(8, 0));
        dateWrap.setOpaque(false);
        dateWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        dateRow.setOpaque(false);
        for (int i = 0; i < TILES_PER_PAGE; i++) {
            DateTile t = new DateTile();
            dateGroup.add(t);
            tiles.add(t);
            dateRow.add(t);
            t.addActionListener(e -> { selectedDate = t.date; rebuildSlots(); });
        }
        prev.addActionListener(e -> { page--; showPage(); });
        next.addActionListener(e -> { page++; showPage(); });
        dateWrap.add(prev, BorderLayout.WEST);
        dateWrap.add(dateRow, BorderLayout.CENTER);
        dateWrap.add(next, BorderLayout.EAST);
        add(dateWrap);
        add(Box.createVerticalStrut(18));

        timeHeading.setFont(new Font("SansSerif", Font.BOLD, 11));
        timeHeading.setForeground(UIKit.TEXT_DARK);
        timeHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeHeading.setBorder(new EmptyBorder(0, 0, 8, 0));
        timeHeading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        add(timeHeading);

        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        for (LocalTime t : PickupSchedule.SLOTS) {
            SlotChip c = new SlotChip(t);
            slotGroup.add(c);
            chips.add(c);
            grid.add(c);
        }
        grid.add(new JLabel());
        add(grid);
        add(Box.createVerticalStrut(8));

        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        info.setForeground(UIKit.TEXT_MUTED);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(info);
    }

    /** Creates a small bold section heading. */
    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(UIKit.TEXT_DARK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    /** Creates a flat arrow button for paging through the dates. */
    private static JButton arrow(String glyph) {
        JButton b = new JButton(glyph);
        b.setFont(new Font("SansSerif", Font.BOLD, 20));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /**
     * Show the days / slots for this module. {@code ignore} is the appointment being rescheduled (its own place
     * does not count against the slot it already sits in). {@code preset} pre-selects a date + time when still free.
     */
    public void setModule(Module m, Appointment ignore, LocalDateTime preset) {
        this.module = m;
        this.ignore = ignore;
        dates = m == null ? new ArrayList<>() : PickupSchedule.bookableDates(m);
        page = 0;
        selectedDate = dates.isEmpty() ? null : dates.get(0);
        if (preset != null && dates.contains(preset.toLocalDate())) {
            selectedDate = preset.toLocalDate();
            page = dates.indexOf(selectedDate) / TILES_PER_PAGE;
        }
        showPage();
        if (preset != null) {
            for (SlotChip c : chips) if (c.time.equals(preset.toLocalTime()) && c.isEnabled()) { c.setSelected(true); break; }
        }
    }

    /** Re-read the places left (another student may have booked meanwhile). */
    public void reload() {
        LocalDateTime keep = getSelectedDateTime();
        if (module != null) setModule(module, ignore, keep);
    }

    /** Shows the date tiles of the current page and refreshes the time slots. */
    private void showPage() {
        int from = page * TILES_PER_PAGE;
        dateGroup.clearSelection();
        for (int i = 0; i < TILES_PER_PAGE; i++) {
            DateTile t = tiles.get(i);
            int idx = from + i;
            if (idx < dates.size()) {
                t.setDate(dates.get(idx));
                t.setVisible(true);
                if (dates.get(idx).equals(selectedDate)) t.setSelected(true);
            } else {
                t.setDate(null);
                t.setVisible(false);
            }
        }
        prev.setVisible(dates.size() > TILES_PER_PAGE);
        next.setVisible(dates.size() > TILES_PER_PAGE);
        prev.setEnabled(page > 0);
        next.setEnabled(from + TILES_PER_PAGE < dates.size());
        rebuildSlots();
    }

    /**
     * Re-checks every time slot for the chosen day: passed, full ("Full"), nearly full ("N left") or
     * free.
     */
    private void rebuildSlots() {
        slotGroup.clearSelection();
        if (selectedDate == null) {
            timeHeading.setText("3. SELECT TIME SLOT");
        } else {
            timeHeading.setText("3. SELECT TIME SLOT (" + selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).toUpperCase() + ")");
        }
        timeHeading.revalidate();
        for (SlotChip c : chips) {
            if (module == null || selectedDate == null) { c.configure("", false); continue; }
            LocalDateTime when = selectedDate.atTime(c.time);
            if (!when.isAfter(LocalDateTime.now())) { c.configure("", false); continue; }
            int left = store.slotsLeft(module, when, ignore);
            if (left <= 0) c.configure("Full", false);
            else if (left <= 3) c.configure(left + " left", true);
            else c.configure("", true);
        }
        if (module == null) info.setText(" ");
        else if (dates.isEmpty()) info.setText("The collection deadline for this module has passed.");
        else info.setText("At most " + module.getSlotLimit() + " student" + (module.getSlotLimit() == 1 ? "" : "s")
                + " per time slot for " + module.getDisplayCode() + ". Faded times are full or already passed.");
    }

    /** The chosen date + time, or null when no (free) slot is selected. */
    public LocalDateTime getSelectedDateTime() {
        if (selectedDate == null) return null;
        for (SlotChip c : chips) if (c.isSelected() && c.isEnabled()) return selectedDate.atTime(c.time);
        return null;
    }

    // ------------------------------------------------------------------ tiles

    /** One day: weekday on top, big day number, month underneath. Selected = black. */
    private static final class DateTile extends JToggleButton {
        LocalDate date;

        DateTile() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(70, 74));
        }

        /** Sets the day shown on this tile. */
        void setDate(LocalDate d) { this.date = d; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color fg;
            if (isSelected()) { g2.setColor(UIKit.BLACK_PILL); g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8); fg = Color.WHITE; }
            else { g2.setColor(Color.WHITE); g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8); g2.setColor(BORDER); g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8); fg = UIKit.TEXT_DARK; }
            if (date != null) {
                g2.setColor(fg);
                center(g2, date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).toUpperCase(), new Font("SansSerif", Font.PLAIN, 10), w, 17);
                center(g2, String.valueOf(date.getDayOfMonth()), new Font("SansSerif", Font.BOLD, 17), w, 40);
                center(g2, date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)), new Font("SansSerif", Font.PLAIN, 10), w, 56);
            }
            g2.dispose();
        }

        /** Draws text horizontally centered at the given baseline. */
        private static void center(Graphics2D g2, String text, Font f, int w, int baseline) {
            g2.setFont(f);
            g2.drawString(text, (w - g2.getFontMetrics().stringWidth(text)) / 2, baseline);
        }
    }

    /** One time slot: outlined chip; selected = grey fill, full / passed = faded. */
    private static final class SlotChip extends JToggleButton {
        final LocalTime time;
        /** Small second line under the time ("Full", "2 left"). */
        private String note = "";

        SlotChip(LocalTime time) {
            this.time = time;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(100, 38));
        }

        /** Sets the small note and whether the slot can be picked. */
        void configure(String note, boolean enabled) {
            this.note = note;
            setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            setToolTipText(!enabled && "Full".equals(note) ? "This time slot is full" : null);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color fg;
            if (!isEnabled()) { g2.setColor(Color.WHITE); g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8); g2.setColor(BORDER_OFF); g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8); fg = new Color(190, 190, 190); }
            else if (isSelected()) { g2.setColor(new Color(225, 225, 225)); g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8); g2.setColor(BORDER); g2.setStroke(new BasicStroke(1.6f)); g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8); fg = UIKit.TEXT_DARK; }
            else { g2.setColor(Color.WHITE); g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8); g2.setColor(BORDER); g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8); fg = UIKit.TEXT_DARK; }
            g2.setColor(fg);
            String label = time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH));
            g2.setFont(new Font("SansSerif", isSelected() ? Font.BOLD : Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int base = note.isEmpty() ? (h + fm.getAscent() - fm.getDescent()) / 2 : h / 2 - 1;
            g2.drawString(label, (w - fm.stringWidth(label)) / 2, base);
            if (!note.isEmpty()) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor("Full".equals(note) ? new Color(200, 90, 90) : new Color(178, 74, 22));
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(note, (w - fm2.stringWidth(note)) / 2, h / 2 + 10);
            }
            g2.dispose();
        }
    }
}
