package moduledistributionsystem.data;

/**
 * The reports the administrator can run on the Reports page. Each one is a real multi-table SQL query
 * (JOIN and GROUP BY); {@link #sql()} is the exact text that {@link Database#runReport} executes and that the
 * "Show SQL" button displays.
 *
 * <p>How to customize: to add a report, add a constant here (label, description, column titles, SQL), add a
 * {@code case} for it in {@code Database.runReport} and in {@code DemoDataStore.buildReportInMemory}, and it
 * appears in the Reports drop-down automatically.
 */
public enum ReportKind {

    /** How busy each module is: appointments per module, split by status (LEFT JOIN + GROUP BY + SUM / COUNT). */
    APPOINTMENTS_BY_MODULE(
            "Appointments by Module",
            "For every module: how many appointments were made and how many are pending, approved, picked up or declined, next to the copies still in stock.",
            new String[]{"Module", "Subject", "Semester", "In Stock", "Total", "Pending", "Approved", "Picked Up", "Declined"},
            "SELECT m.code, m.title, m.semester, m.stocks,\n"
                    + "       COUNT(a.id) AS total,\n"
                    + "       SUM(CASE WHEN a.status = 'PENDING'   THEN 1 ELSE 0 END) AS pending,\n"
                    + "       SUM(CASE WHEN a.status = 'CONFIRMED' THEN 1 ELSE 0 END) AS approved,\n"
                    + "       SUM(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END) AS picked_up,\n"
                    + "       SUM(CASE WHEN a.status = 'CANCELLED' THEN 1 ELSE 0 END) AS declined\n"
                    + "FROM modules m\n"
                    + "LEFT JOIN appointments a ON a.module_id = m.id\n"
                    + "WHERE m.deleted = 0\n"
                    + "GROUP BY m.id, m.code, m.title, m.semester, m.stocks\n"
                    + "ORDER BY total DESC, m.code"),

    /** Every appointment with the student and the module it belongs to (three tables joined). */
    APPOINTMENT_DETAILS(
            "Appointment Details (Student + Module)",
            "Every appointment with the student and the module it belongs to. Three tables are joined: appointments, students and modules.",
            new String[]{"Student", "School ID", "Year", "Module", "Subject", "Pickup Date and Time", "Status", "Handled By"},
            "SELECT s.name, s.id, s.year_level, m.code, m.title, a.appt_at, a.status, a.officer\n"
                    + "FROM appointments a\n"
                    + "INNER JOIN students s ON s.id = a.student_id\n"
                    + "INNER JOIN modules  m ON m.id = a.module_id\n"
                    + "ORDER BY a.appt_at DESC, s.name"),

    /** Which pickup times are used most (GROUP BY the time of day), cancelled bookings not counted. */
    BUSIEST_SLOTS(
            "Busiest Time Slots",
            "How many bookings each pickup time has (cancelled bookings are not counted), to help staff plan the desk.",
            new String[]{"Time Slot", "Bookings", "Different Students"},
            "SELECT DATE_FORMAT(a.appt_at, '%H:%i') AS slot,\n"
                    + "       COUNT(*) AS bookings,\n"
                    + "       COUNT(DISTINCT a.student_id) AS students\n"
                    + "FROM appointments a\n"
                    + "WHERE a.status <> 'CANCELLED'\n"
                    + "GROUP BY slot\n"
                    + "ORDER BY bookings DESC, slot"),

    /** Students and their appointments per year level (LEFT JOIN + GROUP BY + COUNT). */
    STUDENTS_BY_YEAR(
            "Students by Year Level",
            "For every year level: how many students there are, how many appointments they made and how many modules were picked up.",
            new String[]{"Year Level", "Students", "Appointments", "Picked Up"},
            "SELECT s.year_level,\n"
                    + "       COUNT(DISTINCT s.id) AS students,\n"
                    + "       COUNT(a.id) AS appointments,\n"
                    + "       SUM(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END) AS picked_up\n"
                    + "FROM students s\n"
                    + "LEFT JOIN appointments a ON a.student_id = s.id\n"
                    + "GROUP BY s.year_level\n"
                    + "ORDER BY FIELD(s.year_level, 'FIRST_YEAR', 'SECOND_YEAR', 'THIRD_YEAR', 'FOURTH_YEAR')");

    /** Name shown in the Reports drop-down. */
    private final String label;
    /** What the report shows. */
    private final String description;
    /** Column titles. */
    private final String[] columns;
    /** The SQL text. */
    private final String sql;

    ReportKind(String label, String description, String[] columns, String sql) {
        this.label = label;
        this.description = description;
        this.columns = columns;
        this.sql = sql;
    }

    /** Name shown in the Reports drop-down. */
    public String label() { return label; }

    /** One or two sentences explaining what the report shows. */
    public String description() { return description; }

    /** Column titles of the result table. */
    public String[] columns() { return columns.clone(); }

    /** The SQL query text (JOIN / GROUP BY) behind the report. */
    public String sql() { return sql; }

    /** The report with this drop-down label, or the first report when none matches. */
    public static ReportKind byLabel(String label) {
        for (ReportKind k : values()) if (k.label.equals(label)) return k;
        return values()[0];
    }
}
