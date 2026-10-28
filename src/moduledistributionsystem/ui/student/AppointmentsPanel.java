package moduledistributionsystem.ui.student;

import moduledistributionsystem.ui.student.StudentMainFrame;
import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Module;

/**
 * Student page "Appointments" with three tabs:
 * - Upcoming: the student's pending / approved appointments, each with Reschedule and Cancel
 *   (Reschedule is disabled, with a reason, while the module is out of stock or closed);
 * - Book / Reschedule: pick a module, a date and a free time slot (SlotPicker) and confirm;
 * - History: picked-up and cancelled appointments.
 *
 * All rules (slot limit, duplicates, stock) are enforced by DemoDataStore; this class only shows the
 * screens and reports the store's answers.
 */
public class AppointmentsPanel extends JPanel {
    /** The shared data store. */
    private DemoDataStore store;
    /** The student window (told to refresh the other pages after a change). */
    private StudentMainFrame parent;

    private CardLayout tabLayout = new CardLayout();
    private JPanel tabHolder = new JPanel(tabLayout);

    /** Holds the cards of the Upcoming tab. */
    private JPanel upcomingList;
    /** Holds the rows of the History tab. */
    private JPanel historyList;

    /** Module chooser of the Book / Reschedule tab. */
    private JComboBox<Module> moduleCombo;
    /** Date tiles and time-slot chips of the Book / Reschedule tab. */
    private SlotPicker slotPicker;
    /**
     * True while the combo box is being refilled, so that does not count as the student choosing a
     * module.
     */
    private boolean suppressModuleEvents;
    private UUID editingId = null; // non-null => reschedule mode (an id, because the lists are re-read from the database)

    /** The three tab chips. */
    private UIKit.ToggleChip tabUpcoming, tabBook, tabHistory;

    /** Builds the page: title, the tab chips and the three tab contents. */
    public AppointmentsPanel(DemoDataStore store, StudentMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("Appointments");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Book, manage, and track your module pickup appointments");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(UIKit.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(16));

        JPanel tabsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabsRow.setOpaque(false);
        tabsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup tabGroup = new ButtonGroup();
        tabUpcoming = new UIKit.ToggleChip("Upcoming", 8);
        tabBook = new UIKit.ToggleChip("Book / Reschedule", 8);
        tabHistory = new UIKit.ToggleChip("History", 8);
        tabGroup.add(tabUpcoming);
        tabGroup.add(tabBook);
        tabGroup.add(tabHistory);
        tabUpcoming.setSelected(true);
        tabUpcoming.addActionListener(e -> tabLayout.show(tabHolder, "Upcoming"));
        tabBook.addActionListener(e -> { resetBookForm(); tabLayout.show(tabHolder, "Book"); });
        tabHistory.addActionListener(e -> tabLayout.show(tabHolder, "History"));
        tabsRow.add(tabUpcoming);
        tabsRow.add(tabBook);
        tabsRow.add(tabHistory);
        header.add(tabsRow);

        add(header, BorderLayout.NORTH);

        tabHolder.setOpaque(false);
        tabHolder.add(buildUpcomingTab(), "Upcoming");
        tabHolder.add(buildBookTab(), "Book");
        tabHolder.add(buildHistoryTab(), "History");
        add(tabHolder, BorderLayout.CENTER);

        resetBookForm();
        refresh();
    }

    /** Builds the Upcoming tab (a scrolling list of cards). */
    private JPanel buildUpcomingTab() {
        upcomingList = new UIKit.ScrollableBoxPanel();
        upcomingList.setBorder(new EmptyBorder(18, 0, 0, 6));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(UIKit.scroll(upcomingList), BorderLayout.CENTER);
        return wrap;
    }

    /** Builds the History tab (a scrolling list of rows). */
    private JPanel buildHistoryTab() {
        historyList = new UIKit.ScrollableBoxPanel();
        historyList.setBorder(new EmptyBorder(18, 0, 0, 6));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(UIKit.scroll(historyList), BorderLayout.CENTER);
        return wrap;
    }

    /** Builds the Book / Reschedule tab: module chooser, SlotPicker and the Confirm button. */
    private JPanel buildBookTab() {
        UIKit.RoundPanel form = new UIKit.RoundPanel(16, UIKit.CARD_BG);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(22, 24, 22, 24));

        JLabel formTitle = new JLabel("Book or Reschedule the Appointment");
        formTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(formTitle);
        form.add(Box.createVerticalStrut(16));

        form.add(sectionLabel("1. SELECT MODULE FOR PICKUP"));
        moduleCombo = new JComboBox<>();
        moduleCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String text = value == null ? "Choose Module" : ((Module) value).getDisplayCode() + ": " + ((Module) value).getTitle();
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        moduleCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        moduleCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        moduleCombo.addActionListener(e -> onModuleChosen());
        form.add(moduleCombo);
        form.add(Box.createVerticalStrut(16));

        slotPicker = new SlotPicker(store);
        slotPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(slotPicker);
        form.add(Box.createVerticalStrut(18));

        UIKit.RoundButton confirm = new UIKit.RoundButton("Confirm Appointment", UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 10);
        confirm.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirm.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        confirm.addActionListener(e -> confirmBooking());
        form.add(confirm);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(18, 0, 0, 6));
        wrap.add(form, BorderLayout.NORTH);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.add(UIKit.scroll(wrap), BorderLayout.CENTER);
        return outer;
    }

    /** Creates a small bold heading such as "1. SELECT MODULE FOR PICKUP". */
    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(UIKit.TEXT_DARK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    /** Reloads the date and time slots when another module is chosen. */
    private void onModuleChosen() {
        if (suppressModuleEvents || slotPicker == null) return;
        Module m = (Module) moduleCombo.getSelectedItem();
        slotPicker.setModule(m, editingId == null ? null : store.findAppointment(editingId), null);
    }

    /** Modules this student can still book: this term, open, in stock, and not already booked / picked up. */
    private List<Module> bookableModules() {
        List<Module> out = new ArrayList<>();
        for (Module m : store.getCurrentModulesFor(store.getCurrentStudent())) {
            if (store.bookingBlockReason(store.getCurrentStudent(), m) == null) out.add(m);
        }
        out.sort(java.util.Comparator.comparing(Module::getCode));
        return out;
    }

    /**
     * Clears the Book tab (also leaves "reschedule" mode) and fills the module list with the modules
     * the student can still book.
     */
    private void resetBookForm() {
        editingId = null;
        suppressModuleEvents = true;
        moduleCombo.removeAllItems();
        moduleCombo.addItem(null);
        for (Module m : bookableModules()) moduleCombo.addItem(m);
        moduleCombo.setEnabled(true);
        moduleCombo.setSelectedIndex(0);
        suppressModuleEvents = false;
        slotPicker.setModule(null, null, null);
    }

    /**
     * Switches to the Book tab to move an existing appointment. Refused, with the reason, when the
     * module is out of stock or closed.
     */
    private void startReschedule(Appointment a) {
        store.reloadFromDatabase();
        Appointment fresh = store.findAppointment(a.getId());
        if (fresh == null || (fresh.getStatus() != AppointmentStatus.PENDING && fresh.getStatus() != AppointmentStatus.CONFIRMED)) {
            JOptionPane.showMessageDialog(this, "That appointment can't be rescheduled any more.", "Reschedule", JOptionPane.WARNING_MESSAGE);
            refresh();
            return;
        }
        // Rescheduling is blocked while the module is out of stock or not open.
        String why = DemoDataStore.unavailableReason(fresh.getModule());
        if (why != null) {
            JOptionPane.showMessageDialog(this, "Rescheduling is disabled for now.\n\n" + why, "Reschedule", JOptionPane.WARNING_MESSAGE);
            refresh();
            return;
        }
        editingId = fresh.getId();
        suppressModuleEvents = true;
        moduleCombo.removeAllItems();
        moduleCombo.addItem(fresh.getModule());
        moduleCombo.setSelectedIndex(0);
        moduleCombo.setEnabled(false); // the module of an existing booking can't change; cancel and rebook for that
        suppressModuleEvents = false;
        slotPicker.setModule(fresh.getModule(), fresh, fresh.getDateTime());
        tabBook.setSelected(true);
        tabLayout.show(tabHolder, "Book");
    }

    /**
     * Books a new appointment, or saves the new time of the one being rescheduled, through the store,
     * and shows its answer.
     */
    private void confirmBooking() {
        Module m = (Module) moduleCombo.getSelectedItem();
        if (m == null) {
            JOptionPane.showMessageDialog(this, "Please choose a module.", "Book Appointment", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocalDateTime dt = slotPicker.getSelectedDateTime();
        if (dt == null) {
            JOptionPane.showMessageDialog(this, "Please select a time slot that is still free.", "Book Appointment", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean rescheduling = editingId != null;
        // The store re-checks everything (stock, open, slot limit, duplicates, deadline) against the latest data.
        String problem = rescheduling ? store.rescheduleAppointment(store.findAppointment(editingId), dt)
                                      : store.bookAppointment(store.getCurrentStudent(), m, dt);
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, rescheduling ? "Couldn't reschedule" : "Couldn't book", JOptionPane.WARNING_MESSAGE);
            slotPicker.reload();
            return;
        }
        if (rescheduling) {
            JOptionPane.showMessageDialog(this, "Appointment rescheduled. Awaiting confirmation.", "Rescheduled", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Appointment requested. Staff will confirm shortly.\n\n"
                    + "Note: modules are first come, first served. A booking does not reserve a copy.", "Booked", JOptionPane.INFORMATION_MESSAGE);
        }
        resetBookForm();
        tabUpcoming.setSelected(true);
        tabLayout.show(tabHolder, "Upcoming");
        if (parent != null) SwingUtilities.invokeLater(() -> parent.refreshAll());
        refresh();
    }

    /** Asks for confirmation, then cancels the appointment (this frees the time slot). */
    private void cancelAppointment(Appointment a) {
        int ok = JOptionPane.showConfirmDialog(this, "Cancel this appointment?", "Confirm Cancel", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            if (!store.cancelAppointment(a)) {
                JOptionPane.showMessageDialog(this, "That appointment can't be cancelled any more.", "Cancel", JOptionPane.WARNING_MESSAGE);
            }
            if (parent != null) SwingUtilities.invokeLater(() -> parent.refreshAll());
            refresh();
        }
    }

    /**
     * Builds one card of the Upcoming tab: code, status pill, title, date / location / officer chips
     * and the Reschedule and Cancel buttons.
     */
    private JPanel upcomingCard(Appointment a) {
        UIKit.RoundPanel card = new UIKit.RoundPanel(16, UIKit.CARD_BG);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel code = new JLabel(a.getModule().getCode());
        code.setFont(new Font("SansSerif", Font.BOLD, 13));
        head.add(code);
        head.add(statusPill(a.getStatus()));

        JLabel titleLabel = new JLabel(a.getModule().getCode() + " \u2013 " + a.getModule().getTitle());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setBorder(new EmptyBorder(4, 0, 12, 0));

        top.add(head);
        top.add(titleLabel);

        JPanel infoRow = new JPanel(new GridLayout(1, 3, 10, 0));
        infoRow.setOpaque(false);
        infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoRow.add(infoChip("DATE & TIME", a.getDateTime().format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy\nh:mm a", Locale.ENGLISH))));
        infoRow.add(infoChip("LOCATION", a.getLocation()));
        infoRow.add(infoChip("OFFICER", a.getOfficer()));
        top.add(infoRow);

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonsRow.setOpaque(false);
        buttonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonsRow.setBorder(new EmptyBorder(14, 0, 0, 0));
        String blockedReason = DemoDataStore.unavailableReason(a.getModule());
        UIKit.RoundButton reschedule;
        if (blockedReason == null) {
            reschedule = new UIKit.RoundButton("Reschedule", UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 8);
            reschedule.addActionListener(e -> startReschedule(a));
        } else {
            // Module out of stock / not open: rescheduling is disabled until staff make it available again.
            reschedule = new UIKit.RoundButton("Reschedule", new Color(200, 200, 200), new Color(200, 200, 200), new Color(130, 130, 130), 8);
            reschedule.setEnabled(false);
            reschedule.setToolTipText("Disabled: " + blockedReason);
        }
        UIKit.RoundButton cancel = new UIKit.RoundButton("Cancel", UIKit.RED_PILL_BG, new Color(250, 210, 210), UIKit.RED_PILL_FG, 8);
        cancel.addActionListener(e -> cancelAppointment(a));
        buttonsRow.add(reschedule);
        buttonsRow.add(cancel);
        top.add(buttonsRow);
        if (blockedReason != null) {
            boolean outOfStock = a.getModule().isAvailable() && a.getModule().getStocks() <= 0;
            JLabel note = new JLabel("<html>" + (outOfStock ? "Out of stock" : "Not available right now")
                    + " \u2014 rescheduling is disabled. You'll be notified when " + a.getModule().getDisplayCode() + " is available again.</html>");
            note.setFont(new Font("SansSerif", Font.BOLD, 11));
            note.setForeground(UIKit.RED_PILL_FG);
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            note.setBorder(new EmptyBorder(8, 2, 0, 0));
            top.add(note);
        }

        card.add(top, BorderLayout.CENTER);
        return card;
    }

    /** A small gray box with a tiny label above a value. */
    private JPanel infoChip(String label, String value) {
        UIKit.RoundPanel chip = new UIKit.RoundPanel(10, UIKit.CARD_BG_2);
        chip.setLayout(new BoxLayout(chip, BoxLayout.Y_AXIS));
        chip.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel l = new JLabel("<html>" + UIKit.escapeHtml(label) + "</html>");
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setForeground(UIKit.TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel v = new JLabel("<html>" + UIKit.escapeHtml(value).replace("\n", "<br>") + "</html>");
        v.setFont(new Font("SansSerif", Font.BOLD, 12));
        v.setForeground(UIKit.TEXT_DARK);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        chip.add(l);
        chip.add(Box.createVerticalStrut(4));
        chip.add(v);
        return chip;
    }

    /** Builds one row of the History tab. */
    private JPanel historyRow(Appointment a) {
        UIKit.RoundPanel row = new UIKit.RoundPanel(14, UIKit.CARD_BG);
        row.setLayout(new BorderLayout(12, 0));
        row.setBorder(new EmptyBorder(14, 16, 14, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        String prefix = a.getModule().getCode().replaceAll("[0-9]", "");
        row.add(UIKit.westWrap(circleBadge(prefix, new Color(120, 130, 122))), BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        JLabel titleLabel = new JLabel(a.getModule().getCode() + "-" + a.getModule().getTitle());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel meta = new JLabel(a.getDateTime().format(DateTimeFormatter.ofPattern("MMM d,yyyy \u00B7 h:mm a", Locale.ENGLISH)) + " \u00B7 " + a.getLocation());
        meta.setFont(new Font("SansSerif", Font.PLAIN, 11));
        meta.setForeground(UIKit.TEXT_MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(titleLabel);
        center.add(meta);
        row.add(center, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(statusPill(a.getStatus()));
        row.add(right, BorderLayout.EAST);
        return row;
    }

    /** A round badge with the first letters of the module code. */
    private JPanel circleBadge(String text, Color bg) {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(42, 42));
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(Color.WHITE);
        p.add(l);
        return p;
    }

    /** The colored status pill (Pending, Confirmed, Complete, Cancelled). */
    private UIKit.Pill statusPill(AppointmentStatus status) {
        switch (status) {
            case CONFIRMED: return new UIKit.Pill("Confirmed", UIKit.GREEN_PILL_BG, new Color(20, 120, 60));
            case COMPLETED: return new UIKit.Pill("Complete", UIKit.GREEN_PILL_BG, new Color(20, 120, 60));
            case CANCELLED: return new UIKit.Pill("Cancelled", UIKit.RED_PILL_BG, UIKit.RED_PILL_FG);
            case PENDING:
            default: return new UIKit.Pill("Pending", UIKit.AMBER_PILL_BG, UIKit.AMBER_PILL_FG);
        }
    }

    /** Re-reads the student's appointments and rebuilds the Upcoming and History tabs. */
    public void refresh() {
        upcomingList.removeAll();
        historyList.removeAll();
        boolean anyUpcoming = false, anyHistory = false;
        for (Appointment a : store.getAppointmentsForStudent(store.getCurrentStudent().getId())) {
            if (a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.PENDING) {
                if (anyUpcoming) upcomingList.add(Box.createVerticalStrut(14));
                upcomingList.add(upcomingCard(a));
                anyUpcoming = true;
            } else {
                if (anyHistory) historyList.add(Box.createVerticalStrut(10));
                historyList.add(historyRow(a));
                anyHistory = true;
            }
        }
        if (!anyUpcoming) upcomingList.add(emptyState("No upcoming appointments."));
        if (!anyHistory) historyList.add(emptyState("No appointment history yet."));
        upcomingList.revalidate();
        upcomingList.repaint();
        historyList.revalidate();
        historyList.repaint();
    }

    /** The gray "nothing here yet" message. */
    private JLabel emptyState(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        l.setForeground(UIKit.TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
