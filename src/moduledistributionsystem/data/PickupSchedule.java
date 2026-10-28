package moduledistributionsystem.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import moduledistributionsystem.model.Module;

/**
 * The pickup desk's time slots and the booking window, in one place so the Modules page, the
 * Appointments page and the data layer all agree.
 *
 * How to customize: change SLOTS to change the available times (the screens and the validation follow
 * automatically), and BOOKING_DAYS_AHEAD to change how far ahead a student can book.
 */
public final class PickupSchedule {
    /** Utility class: only static members, so it is never instantiated. */
    private PickupSchedule() { }

    /** How many days ahead a student can book (never past the module's collect-by date). */
    public static final int BOOKING_DAYS_AHEAD = 14;

    public static final LocalTime[] SLOTS = {
            LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30),
            LocalTime.of(11, 0), LocalTime.of(11, 30), LocalTime.of(14, 0), LocalTime.of(14, 30),
            LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 0)
    };

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** "9:00 AM", "2:30 PM" ... */
    public static String label(LocalTime t) { return t.format(LABEL); }

    /** Days a student may pick for this module: today up to the collect-by date (at most {@link #BOOKING_DAYS_AHEAD} days out). */
    public static List<LocalDate> bookableDates(Module m) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= BOOKING_DAYS_AHEAD; i++) {
            LocalDate d = today.plusDays(i);
            if (m != null && m.getCollectBy() != null && d.isAfter(m.getCollectBy())) break;
            out.add(d);
        }
        return out;
    }

    /** Why this date + time cannot be booked for the module (in the past, or after the collect-by date), or null if it is fine. */
    public static String problemWith(Module m, LocalDateTime when) {
        if (when == null) return "Please choose a date and a time slot.";
        if (!when.isAfter(LocalDateTime.now())) return "That time has already passed. Please pick a later time slot.";
        if (m != null && m.getCollectBy() != null && when.toLocalDate().isAfter(m.getCollectBy())) {
            return "That date is after this module's collection deadline (" + m.getCollectBy() + "). Please pick an earlier day.";
        }
        boolean validSlot = false;
        for (LocalTime t : SLOTS) if (t.equals(when.toLocalTime())) { validSlot = true; break; }
        if (!validSlot) return "Please choose one of the listed time slots.";
        return null;
    }
}
