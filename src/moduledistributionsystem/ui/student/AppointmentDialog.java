package moduledistributionsystem.ui.student;

import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.data.PickupSchedule;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.Module;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Small window opened from the Modules page to book a pickup: choose a day and a time. Times that are
 * full or already passed are shown in gray and can not be confirmed; each time shows how many places
 * are left.
 *
 * The caller reads isConfirmed() and getSelectedDateTime() after the window closes, then calls
 * DemoDataStore.bookAppointment.
 */
public class AppointmentDialog extends JDialog {
    /** True when the student clicked Confirm. */
    private boolean confirmed = false;
    /** The chosen day and time. */
    private LocalDateTime selectedDateTime;

    /** One entry of the time list: the time plus how many places are left that day. */
    private static final class TimeItem {
        final LocalTime time;
        final String note;
        final boolean free;
        TimeItem(LocalTime time, String note, boolean free) { this.time = time; this.note = note; this.free = free; }
        @Override public String toString() { return String.format("%02d:%02d", time.getHour(), time.getMinute()) + (note.isEmpty() ? "" : "  \u2014  " + note); }
    }

    /** @param existing the appointment being rescheduled, or null for a new booking */
    public AppointmentDialog(Window owner, DemoDataStore store, Module module, Appointment existing) {
        super(owner, "Book or Reschedule Appointment", ModalityType.APPLICATION_MODAL);
        setSize(420, 340);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        JPanel content = new JPanel();
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(new JLabel("Module: " + module.getDisplayCode() + " \u2014 " + module.getTitle()));
        content.add(Box.createVerticalStrut(8));
        content.add(new JLabel("Select Date"));
        JComboBox<String> dateCombo = new JComboBox<>();
        for (LocalDate d : PickupSchedule.bookableDates(module)) dateCombo.addItem(d.toString());
        content.add(dateCombo);
        content.add(Box.createVerticalStrut(8));
        content.add(new JLabel("Select Time"));
        JComboBox<TimeItem> timeCombo = new JComboBox<>();
        // Full / passed times are shown but greyed out, and can't be confirmed.
        timeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                Component c = super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof TimeItem && !((TimeItem) value).free) c.setForeground(new Color(160, 160, 160));
                return c;
            }
        });
        content.add(timeCombo);
        content.add(Box.createVerticalStrut(8));
        JLabel hint = new JLabel("<html>At most " + module.getSlotLimit() + " student" + (module.getSlotLimit() == 1 ? "" : "s")
                + " per time slot, to keep the pickup desk from getting crowded.</html>");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hint.setForeground(new Color(110, 112, 118));
        content.add(hint);

        Runnable fillTimes = () -> {
            timeCombo.removeAllItems();
            String ds = (String) dateCombo.getSelectedItem();
            if (ds == null) return;
            LocalDate day = LocalDate.parse(ds);
            TimeItem firstFree = null;
            for (LocalTime t : PickupSchedule.SLOTS) {
                LocalDateTime when = day.atTime(t);
                TimeItem item;
                if (!when.isAfter(LocalDateTime.now())) item = new TimeItem(t, "passed", false);
                else {
                    int left = store.slotsLeft(module, when, existing);
                    item = left <= 0 ? new TimeItem(t, "full", false) : new TimeItem(t, left + " left", true);
                }
                timeCombo.addItem(item);
                if (firstFree == null && item.free) firstFree = item;
            }
            if (existing != null && existing.getDateTime().toLocalDate().equals(day)) {
                for (int i = 0; i < timeCombo.getItemCount(); i++) {
                    if (timeCombo.getItemAt(i).time.equals(existing.getDateTime().toLocalTime()) && timeCombo.getItemAt(i).free) { timeCombo.setSelectedIndex(i); return; }
                }
            }
            if (firstFree != null) timeCombo.setSelectedItem(firstFree);
        };
        dateCombo.addActionListener(e -> fillTimes.run());
        fillTimes.run();

        add(content, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton confirm = new JButton("Confirm");
        JButton cancel = new JButton("Cancel");
        buttons.add(cancel);
        buttons.add(confirm);
        add(buttons, BorderLayout.SOUTH);

        confirm.addActionListener(e -> {
            String date = (String) dateCombo.getSelectedItem();
            TimeItem time = (TimeItem) timeCombo.getSelectedItem();
            if (date == null || time == null || !time.free) {
                JOptionPane.showMessageDialog(this, "Please choose a time that is still free (not full and not already passed).",
                        "Choose a time", JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedDateTime = LocalDate.parse(date).atTime(time.time);
            confirmed = true;
            dispose();
        });
        cancel.addActionListener(e -> dispose());
    }

    /** True when the student confirmed a time. */
    public boolean isConfirmed() { return confirmed; }
    /** The chosen day and time (valid after isConfirmed()). */
    public LocalDateTime getSelectedDateTime() { return selectedDateTime; }
}
