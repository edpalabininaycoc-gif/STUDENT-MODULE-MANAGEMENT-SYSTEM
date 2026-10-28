package moduledistributionsystem.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.model.YearLevel;

/**
 * The result of one report: its title, column titles and the rows (every cell is already formatted text).
 * It is a read-only snapshot: the Reports page shows it, exports it to CSV and prints it.
 */
public final class Report {

    /** Which report this result belongs to. */
    private final ReportKind kind;
    /** The formatted rows (read-only). */
    private final List<String[]> rows;
    /** True when a real SQL query produced the rows. */
    private final boolean fromDatabase;

    /**
     * @param kind         which report this is
     * @param rows         the formatted rows (copied)
     * @param fromDatabase true when a real SQL query produced it, false when it was computed in memory (demo mode)
     */
    public Report(ReportKind kind, List<String[]> rows, boolean fromDatabase) {
        this.kind = kind;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.fromDatabase = fromDatabase;
    }

    /** Which report this is. */
    public ReportKind getKind() { return kind; }

    /** Column titles. */
    public String[] getColumns() { return kind.columns(); }

    /** The formatted rows. */
    public List<String[]> getRows() { return rows; }

    /** True when a real SQL query produced this report. */
    public boolean isFromDatabase() { return fromDatabase; }

    // ---- formatting shared by the SQL path and the in-memory path, so both produce identical text ----

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** "Pending", "Approved", "Picked Up" or "Declined". */
    public static String statusText(AppointmentStatus s) {
        switch (s) {
            case CONFIRMED: return "Approved";
            case COMPLETED: return "Picked Up";
            case CANCELLED: return "Declined";
            default: return "Pending";
        }
    }

    /** Same as {@link #statusText(AppointmentStatus)} for the name stored in the database ("CONFIRMED"...). */
    public static String statusText(String storedName) {
        return statusText(AppointmentStatus.valueOf(storedName));
    }

    /** "2nd Year", or "Not set" when the year level is missing. */
    public static String yearText(YearLevel y) { return y == null ? "Not set" : y.toString(); }

    /** Same as {@link #yearText(YearLevel)} for the name stored in the database ("SECOND_YEAR"...). */
    public static String yearText(String storedName) {
        return storedName == null ? "Not set" : yearText(YearLevel.valueOf(storedName));
    }

    /** "Oct 7, 2026 10:00 AM". */
    public static String whenText(LocalDateTime t) { return t == null ? "" : t.format(WHEN); }

    /** The registrar's way of writing a module code, with a space: "ITE031" -> "ITE 031". */
    public static String codeText(String code) {
        return code == null ? "" : code.replaceFirst("^([A-Za-z]+)\\s*([0-9].*)$", "$1 $2");
    }

    /** "10:00 AM" for a time stored as "10:00". */
    public static String slotText(String hhmm) { return java.time.LocalTime.parse(hhmm).format(SLOT); }
}
