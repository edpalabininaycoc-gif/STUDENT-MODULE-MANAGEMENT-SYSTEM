package moduledistributionsystem.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import moduledistributionsystem.model.Admin;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.model.Feedback;
import moduledistributionsystem.model.LogStatus;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.model.ModuleStatus;
import moduledistributionsystem.model.NotificationItem;
import moduledistributionsystem.model.QueryPriority;
import moduledistributionsystem.model.QueryStatus;
import moduledistributionsystem.model.Staff;
import moduledistributionsystem.model.Student;
import moduledistributionsystem.model.StudentQuery;
import moduledistributionsystem.model.SystemLogEntry;
import moduledistributionsystem.model.YearLevel;

/**
 * All the SQL of the app lives in this class (JDBC; MySQL or MariaDB such as XAMPP's).
 *
 * How the pieces fit: DemoDataStore keeps its lists in memory for the screens, loads them from here at
 * start-up (load... methods) and calls this class to save every change (insert / update / save / delete
 * methods). Nothing else in the program writes SQL.
 *
 * Safety rules used everywhere in this file:
 * - every statement is a PreparedStatement with ? placeholders (user text is never glued into SQL, so
 *   SQL injection is not possible);
 * - changes that must happen together (booking a slot, handing out the last copy) run inside a
 *   transaction;
 * - new columns are added automatically with ensureColumn, so an older database upgrades itself.
 *
 * How to customize: to save a new field, add the column to schema.sql (and an ensureColumn line in
 * connect() for databases that already exist), then add it to the matching load... / save... method
 * below.
 */
public final class Database {
    /** If the connection was idle for longer than this (milliseconds), it is tested before being reused. */
    private static final long IDLE_CHECK_MS = 10_000;

    /** Connection settings (host, port, user, password...). */
    private final DbConfig cfg;
    /** The loaded JDBC driver (MySQL or MariaDB connector). */
    private final Driver driver;
    /** The one shared connection. Always get it through conn(), never directly. */
    private Connection conn;
    /** When the connection was last used (milliseconds). */
    private long lastUsed;

    /** Private on purpose: create a Database only through connect(). */
    private Database(DbConfig cfg, Driver driver) {
        this.cfg = cfg;
        this.driver = driver;
    }

    // ================================================================== connecting

    /** Loads the JDBC driver, creates the database if it does not exist yet, connects and creates the tables. */
    public static Database connect(DbConfig cfg) throws SQLException {
        if (!cfg.name.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("db.name may only contain letters, digits and underscores (got \"" + cfg.name + "\").");
        }
        Database db = new Database(cfg, loadDriver(cfg));
        db.createDatabaseIfMissing();
        db.conn = db.open(cfg.databaseUrl());
        db.lastUsed = System.currentTimeMillis();
        db.runSchema();
        db.ensureColumn("students", "year_level", "VARCHAR(20) NULL");
        db.ensureColumn("modules", "year_level", "VARCHAR(20) NULL");
        db.ensureColumn("appointments", "archived", "TINYINT(1) NOT NULL DEFAULT 0");
        db.ensureColumn("student_queries", "archived", "TINYINT(1) NOT NULL DEFAULT 0");
        db.ensureColumn("students", "archived", "TINYINT(1) NOT NULL DEFAULT 0");
        db.ensureColumn("staff", "archived", "TINYINT(1) NOT NULL DEFAULT 0");
        db.ensureColumn("admins", "archived", "TINYINT(1) NOT NULL DEFAULT 0");
        db.ensureColumn("modules", "slot_limit", "INT NOT NULL DEFAULT 10");
        db.normalizeSchema();   // 3NF clean-up + foreign keys for databases that were created by an older version
        return db;
    }

    /** Short text describing the server, for messages. */
    public String describe() { return cfg.describe(); }

    /** Opens a new connection to the given JDBC address with the configured user and password. */
    private Connection open(String url) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", cfg.user);
        p.setProperty("password", cfg.password);
        Connection c = driver.connect(url, p);
        if (c == null) throw new SQLException("The JDBC driver did not accept the URL " + url);
        return c;
    }

    /** Creates the database on the server when it does not exist yet (a failure is not fatal). */
    private void createDatabaseIfMissing() {
        try (Connection c = open(cfg.serverUrl()); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + cfg.name + "` CHARACTER SET utf8mb4");
        } catch (SQLException e) {
            // Not fatal: the database may already exist and this user may simply not be allowed to create one.
            // If it really is missing, the next connect fails with a clear message.
            System.err.println("Could not run CREATE DATABASE (" + e.getMessage() + "); trying to use the existing database.");
        }
    }

    /**
     * Returns the shared connection and reconnects automatically when it was closed or went stale.
     * Synchronized so two threads never reconnect at once.
     */
    private synchronized Connection conn() throws SQLException {
        long now = System.currentTimeMillis();
        if (conn == null || conn.isClosed() || (now - lastUsed > IDLE_CHECK_MS && !conn.isValid(2))) {
            conn = open(cfg.databaseUrl());
        }
        lastUsed = now;
        return conn;
    }

    /** Closes the connection (called when the application exits). */
    public synchronized void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) { }
        conn = null;
    }

    /** Runs resources/schema.sql: every CREATE TABLE IF NOT EXISTS statement in it, one by one. */
    private void runSchema() throws SQLException {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/moduledistributionsystem/resources/schema.sql")) {
            if (in == null) throw new SQLException("schema.sql was not found inside the application.");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.trim().startsWith("--")) continue;
                    sb.append(line).append('\n');
                }
            }
            sql = sb.toString();
        } catch (IOException e) {
            throw new SQLException("Could not read schema.sql: " + e.getMessage(), e);
        }
        try (Statement st = conn().createStatement()) {
            for (String stmt : sql.split(";")) {
                if (!stmt.trim().isEmpty()) st.executeUpdate(stmt);
            }
        }
    }

    /**
     * Adds a column to an existing table only if it isn't there yet -- checked through JDBC
     * metadata rather than "ALTER TABLE ... ADD COLUMN IF NOT EXISTS", because that clause's
     * SQL-version support is inconsistent (MariaDB has had it since 10.0.2, but plain MySQL
     * only added it in 8.0.29, and plenty of XAMPP installs still ship older than that -- when
     * unsupported, it doesn't cleanly error either, it can just silently not add the column,
     * which is exactly the "Unknown column" failure this replaces). This works the same on any
     * MySQL/MariaDB version, old or new.
     */
    private void ensureColumn(String table, String column, String columnDefSql) throws SQLException {
        // Pin the lookup to the database we're connected to: with a null catalog the driver may search
        // EVERY database on the server, so a same-named table in another database could look like a match.
        try (ResultSet rs = conn().getMetaData().getColumns(conn().getCatalog(), null, table, column)) {
            if (rs.next()) return; // already there
        }
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDefSql);
        }
    }

    // ---- Schema upgrade: third normal form + foreign keys ----

    /** True when the table has this column (looked up in the connected database only). */
    private boolean columnExists(String table, String column) throws SQLException {
        try (ResultSet rs = conn().getMetaData().getColumns(conn().getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    /** True when the table already has a foreign key on this column. */
    private boolean hasForeignKey(String table, String column) throws SQLException {
        try (ResultSet rs = conn().getMetaData().getImportedKeys(conn().getCatalog(), null, table)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("FKCOLUMN_NAME"))) return true;
            }
        }
        return false;
    }

    /**
     * Brings a database created by an older version up to the current design:
     * <ol>
     *   <li>removes columns that only repeated a fact stored elsewhere (a student's name, a module's location),
     *       because those are read with a JOIN now (this is what third normal form asks for);</li>
     *   <li>adds the foreign keys that tie the tables together.</li>
     * </ol>
     * Nothing here is allowed to stop the program from starting: every step is tried on its own and a failure only
     * prints a warning to the console (for example a foreign key can not be added while old rows point to a student
     * that no longer exists).
     */
    private void normalizeSchema() {
        dropRedundantColumn("appointments", "student_name", "VARCHAR(120) NULL");
        dropRedundantColumn("appointments", "location", "VARCHAR(100) NULL");
        dropRedundantColumn("student_queries", "student_name", "VARCHAR(120) NULL");
        ensureForeignKey("appointments", "fk_appointments_student", "student_id", "students", "id");
        ensureForeignKey("notifications", "fk_notifications_student", "student_id", "students", "id");
        ensureForeignKey("feedback", "fk_feedback_student", "student_id", "students", "id");
        ensureForeignKey("student_queries", "fk_queries_student", "student_id", "students", "id");
        ensureForeignKey("student_module_overrides", "fk_overrides_student", "student_id", "students", "id");
    }

    /**
     * Drops a column that repeated information kept in another table. If it can not be dropped, it is at least made
     * optional, so inserts that no longer fill it still work.
     *
     * @param optionalDefinition the column type followed by NULL, used by the fallback
     */
    private void dropRedundantColumn(String table, String column, String optionalDefinition) {
        try {
            if (!columnExists(table, column)) return;
            try (Statement st = conn().createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " DROP COLUMN " + column);
            }
            System.out.println("Schema upgrade: removed redundant column " + table + "." + column);
        } catch (SQLException e) {
            System.err.println("Could not drop " + table + "." + column + " (" + e.getMessage() + "); making it optional instead.");
            try (Statement st = conn().createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " MODIFY " + column + " " + optionalDefinition);
            } catch (SQLException ignored) {
                System.err.println("Could not make " + table + "." + column + " optional either: " + ignored.getMessage());
            }
        }
    }

    /** Adds a foreign key unless the column already has one. A failure only prints a warning. */
    private void ensureForeignKey(String table, String name, String column, String refTable, String refColumn) {
        try {
            if (hasForeignKey(table, column)) return;
            try (Statement st = conn().createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " FOREIGN KEY (" + column
                        + ") REFERENCES " + refTable + " (" + refColumn + ")");
            }
            System.out.println("Schema upgrade: added foreign key " + name);
        } catch (SQLException e) {
            System.err.println("Could not add foreign key " + name + " (" + e.getMessage() + "). "
                    + "Old rows that point to a missing student can cause this; the program keeps working without it.");
        }
    }

    // ---- JDBC driver lookup ----
    // The driver .jar is not bundled (licence + size). Either add it to the NetBeans project libraries, or
    // simply drop it in a "lib" folder next to the project / the app jar: it is picked up from there.

    /**
     * Finds and loads the JDBC driver .jar (from the "lib" folder next to db.properties, or db.libDir)
     * so no driver has to be added to the project by hand.
     */
    private static Driver loadDriver(DbConfig cfg) throws SQLException {
        boolean maria = "mariadb".equals(cfg.type);
        String className = cfg.driverClass != null ? cfg.driverClass
                : (maria ? "org.mariadb.jdbc.Driver" : "com.mysql.cj.jdbc.Driver");
        String url = cfg.serverUrl();
        StringBuilder details = new StringBuilder();

        // 1) already on the classpath (e.g. the jar was added to the NetBeans project libraries)
        try {
            return (Driver) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException ignored) {
            // normal when the jar is not a project library
        } catch (ReflectiveOperationException | LinkageError e) {
            details.append("Class ").append(className).append(" is on the classpath but failed to load: ").append(e).append('\n');
        }
        Driver viaServices = fromServices(Database.class.getClassLoader(), url, details);
        if (viaServices != null) return viaServices;

        // 2) any .jar in the lib folders
        List<File> folders = driverFolders(cfg);
        List<URL> jars = new ArrayList<>();
        List<String> jarNames = new ArrayList<>();
        for (File dir : folders) {
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
            if (files == null) continue;
            for (File f : files) {
                try {
                    jars.add(f.toURI().toURL());
                    jarNames.add(f.getAbsolutePath());
                } catch (java.net.MalformedURLException ignored) { }
            }
        }
        if (!jars.isEmpty()) {
            URLClassLoader loader = new URLClassLoader(jars.toArray(new URL[0]), Database.class.getClassLoader());
            try {
                return (Driver) Class.forName(className, true, loader).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                details.append("Class ").append(className).append(" is not inside the jar(s) found.\n");
            } catch (ReflectiveOperationException | LinkageError e) {
                details.append("Class ").append(className).append(" was found but failed to load: ").append(e).append('\n');
            }
            // The jar may use a different driver class name (or a different version): ask the jar itself.
            Driver d = fromServices(loader, url, details);
            if (d != null) return d;
        }

        StringBuilder msg = new StringBuilder("JDBC driver was not found (looked for " + className + ").\n");
        if (jarNames.isEmpty()) {
            msg.append("No .jar file exists in any of these folders:\n");
            for (File f : folders) msg.append("  ").append(f.getAbsolutePath()).append('\n');
            msg.append("Put the MySQL Connector/J .jar in one of them (the \"lib\" folder next to db.properties), then restart.");
        } else {
            msg.append("Jar(s) found: ");
            for (String n : jarNames) msg.append("\n  ").append(n);
            msg.append("\n").append(details);
        }
        throw new SQLException(msg.toString().trim());
    }

    /** Finds a JDBC driver through the standard META-INF/services registration inside a jar. */
    private static Driver fromServices(ClassLoader loader, String url, StringBuilder details) {
        try {
            for (Driver d : ServiceLoader.load(Driver.class, loader)) {
                if (d.acceptsURL(url)) return d;
            }
        } catch (ServiceConfigurationError | SQLException e) {
            details.append("Could not load a driver from the jar: ").append(e).append('\n');
        }
        return null;
    }

    /** The folders searched for the driver .jar. */
    private static List<File> driverFolders(DbConfig cfg) {
        Set<File> dirs = new LinkedHashSet<>();
        if (cfg.libDir != null) dirs.add(new File(cfg.libDir));
        dirs.add(new File("lib"));
        dirs.add(new File("libs"));
        dirs.add(new File("dist/lib"));
        try {
            File here = new File(Database.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File base = here.isFile() ? here.getParentFile() : here;
            if (base != null) {
                dirs.add(new File(base, "lib"));
                if (base.getParentFile() != null) dirs.add(new File(base.getParentFile(), "lib"));
            }
        } catch (Exception ignored) { }
        return new ArrayList<>(dirs);
    }

    // ================================================================== small JDBC helpers

    /**
     * Fills the ? placeholders of a PreparedStatement. Understands null, UUID, enums (stored by name),
     * Boolean, Integer, String and dates.
     */
    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            int idx = i + 1;
            if (v == null) ps.setNull(idx, Types.NULL);
            else if (v instanceof UUID || v instanceof Enum) ps.setString(idx, v instanceof UUID ? v.toString() : ((Enum<?>) v).name());
            else if (v instanceof Boolean) ps.setBoolean(idx, (Boolean) v);
            else if (v instanceof Integer) ps.setInt(idx, (Integer) v);
            else if (v instanceof String) ps.setString(idx, (String) v);
            else if (v instanceof LocalDate || v instanceof LocalDateTime) ps.setObject(idx, v);
            else ps.setObject(idx, v);
        }
    }

    /** Runs one INSERT / UPDATE / DELETE with parameters and returns how many rows changed. */
    private int exec(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** Reads a UUID stored as text in the given column. */
    private static UUID uuid(ResultSet rs, String col) throws SQLException { return UUID.fromString(rs.getString(col)); }

    /** Reads a date-and-time column. */
    private static LocalDateTime dt(ResultSet rs, String col) throws SQLException { return rs.getObject(col, LocalDateTime.class); }

    // ================================================================== loading

    /** True when there are no accounts at all, i.e. a brand-new database that should get the sample data. */
    public boolean isEmpty() throws SQLException {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM students) + (SELECT COUNT(*) FROM staff) + (SELECT COUNT(*) FROM admins)")) {
            return rs.next() && rs.getLong(1) == 0;
        }
    }

    /** Loads every student account, in the order they were created. */
    public List<Student> loadStudents() throws SQLException {
        List<Student> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, email, password_hash, is_active, archived, year_level FROM students ORDER BY seq")) {
            while (rs.next()) {
                Student s = new Student(rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("password_hash"));
                s.setActive(rs.getBoolean("is_active"));
                s.setArchived(rs.getBoolean("archived"));
                String yl = rs.getString("year_level");
                if (yl != null) s.setYearLevel(YearLevel.valueOf(yl));
                out.add(s);
            }
        }
        return out;
    }

    /** Loads every staff account. */
    public List<Staff> loadStaff() throws SQLException {
        List<Staff> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, email, password_hash, department, title, is_active, archived FROM staff ORDER BY seq")) {
            while (rs.next()) {
                Staff s = new Staff(rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("password_hash"),
                        rs.getString("department"), rs.getString("title"));
                s.setActive(rs.getBoolean("is_active"));
                s.setArchived(rs.getBoolean("archived"));
                out.add(s);
            }
        }
        return out;
    }

    /** Loads every administrator account. */
    public List<Admin> loadAdmins() throws SQLException {
        List<Admin> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, email, password_hash, title, is_active, archived FROM admins ORDER BY seq")) {
            while (rs.next()) {
                Admin a = new Admin(rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("password_hash"),
                        rs.getString("title"));
                a.setActive(rs.getBoolean("is_active"));
                a.setArchived(rs.getBoolean("archived"));
                out.add(a);
            }
        }
        return out;
    }

    /**
     * Returns the modules that are still in the inventory. {@code allById} is filled with every module,
     * including removed ones, because old appointments may still point at them.
     */
    public List<Module> loadModules(Map<UUID, Module> allById) throws SQLException {
        List<Module> active = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, code, title, credits, location, collect_by, status, semester, department, stocks, deleted, year_level, slot_limit "
                     + "FROM modules ORDER BY seq")) {
            while (rs.next()) {
                Module m = Module.restore(uuid(rs, "id"), rs.getString("code"), rs.getString("title"), rs.getInt("credits"),
                        rs.getString("location"), rs.getObject("collect_by", LocalDate.class),
                        ModuleStatus.valueOf(rs.getString("status")), rs.getString("semester"), rs.getString("department"),
                        rs.getInt("stocks"));
                String yl = rs.getString("year_level");
                if (yl != null) m.setYearLevel(YearLevel.valueOf(yl));
                m.setSlotLimit(rs.getInt("slot_limit"));
                allById.put(m.getId(), m);
                if (!rs.getBoolean("deleted")) active.add(m);
            }
        }
        return active;
    }

    /**
     * Loads every appointment. Each one is linked to its Module object through the given id -> module
     * map.
     */
    public List<Appointment> loadAppointments(Map<UUID, Module> modulesById) throws SQLException {
        List<Appointment> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             // The student's name and the module's location are NOT stored in appointments (3NF): they are joined in.
             ResultSet rs = st.executeQuery("SELECT a.id, a.module_id, a.appt_at, m.location AS module_location, a.status, a.officer, "
                     + "a.student_id, s.name AS student_name, a.requested_at, a.archived "
                     + "FROM appointments a "
                     + "JOIN modules m ON m.id = a.module_id "
                     + "LEFT JOIN students s ON s.id = a.student_id "
                     + "ORDER BY a.requested_at, a.id")) {
            while (rs.next()) {
                Module m = modulesById.get(UUID.fromString(rs.getString("module_id")));
                if (m == null) continue;
                out.add(Appointment.restore(uuid(rs, "id"), m, dt(rs, "appt_at"), rs.getString("module_location"),
                        AppointmentStatus.valueOf(rs.getString("status")), rs.getString("officer"),
                        rs.getString("student_name"), rs.getString("student_id"), dt(rs, "requested_at"),
                        rs.getBoolean("archived")));
            }
        }
        return out;
    }

    // ---- Reports (multi-table JOIN / GROUP BY queries) ----

    /**
     * Runs one of the administrator's reports as a real SQL query ({@link ReportKind#sql()}) and formats every cell
     * as text, using the same formatting helpers as the in-memory version in DemoDataStore.
     */
    public Report runReport(ReportKind kind) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery(kind.sql())) {
            while (rs.next()) {
                switch (kind) {
                    case APPOINTMENTS_BY_MODULE:
                        rows.add(new String[]{Report.codeText(rs.getString("code")), rs.getString("title"), rs.getString("semester"),
                                String.valueOf(rs.getInt("stocks")), String.valueOf(rs.getLong("total")), String.valueOf(rs.getLong("pending")),
                                String.valueOf(rs.getLong("approved")), String.valueOf(rs.getLong("picked_up")), String.valueOf(rs.getLong("declined"))});
                        break;
                    case APPOINTMENT_DETAILS:
                        rows.add(new String[]{rs.getString("name"), rs.getString("id"), Report.yearText(rs.getString("year_level")),
                                Report.codeText(rs.getString("code")), rs.getString("title"), Report.whenText(dt(rs, "appt_at")),
                                Report.statusText(rs.getString("status")), rs.getString("officer")});
                        break;
                    case BUSIEST_SLOTS:
                        rows.add(new String[]{Report.slotText(rs.getString("slot")), String.valueOf(rs.getLong("bookings")),
                                String.valueOf(rs.getLong("students"))});
                        break;
                    default: // STUDENTS_BY_YEAR
                        rows.add(new String[]{Report.yearText(rs.getString("year_level")), String.valueOf(rs.getLong("students")),
                                String.valueOf(rs.getLong("appointments")), String.valueOf(rs.getLong("picked_up"))});
                        break;
                }
            }
        }
        return new Report(kind, rows, true);
    }

    /** Newest first, like the in-memory list. */
    public List<NotificationItem> loadNotifications() throws SQLException {
        List<NotificationItem> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, student_id, title, message, created_at, is_read FROM notifications "
                     + "ORDER BY created_at DESC, id")) {
            while (rs.next()) {
                out.add(NotificationItem.restore(uuid(rs, "id"), rs.getString("student_id"), rs.getString("title"),
                        rs.getString("message"), dt(rs, "created_at"), rs.getBoolean("is_read")));
            }
        }
        return out;
    }

    /** Loads every feedback entry. */
    public List<Feedback> loadFeedbacks() throws SQLException {
        List<Feedback> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, student_id, category, rating, comment_text, submitted_at FROM feedback "
                     + "ORDER BY submitted_at, id")) {
            while (rs.next()) {
                out.add(Feedback.restore(uuid(rs, "id"), rs.getString("student_id"), rs.getString("category"),
                        rs.getInt("rating"), rs.getString("comment_text"), dt(rs, "submitted_at")));
            }
        }
        return out;
    }

    /** Loads every student query (ticket). */
    public List<StudentQuery> loadQueries() throws SQLException {
        List<StudentQuery> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             // The student's name is joined in from the students table (3NF), not stored in every ticket.
             ResultSet rs = st.executeQuery("SELECT q.id, q.ticket_number, q.student_id, s.name AS student_name, q.subject_code, q.category, "
                     + "q.priority, q.status, q.message, q.submitted_at, q.answered_at, q.reply, q.archived "
                     + "FROM student_queries q LEFT JOIN students s ON s.id = q.student_id ORDER BY q.ticket_number")) {
            while (rs.next()) {
                out.add(StudentQuery.restore(uuid(rs, "id"), rs.getInt("ticket_number"), rs.getString("student_id"),
                        rs.getString("student_name"), rs.getString("subject_code"), rs.getString("category"),
                        QueryPriority.valueOf(rs.getString("priority")), QueryStatus.valueOf(rs.getString("status")),
                        rs.getString("message"), dt(rs, "submitted_at"), dt(rs, "answered_at"), rs.getString("reply"),
                        rs.getBoolean("archived")));
            }
        }
        return out;
    }

    /** Most recent entries first (the System Activity screen shows the newest on top). */
    public List<SystemLogEntry> loadLogs(int limit) throws SQLException {
        List<SystemLogEntry> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT created_at, user_label, action_text, ip_address, status "
                + "FROM system_logs ORDER BY created_at DESC, id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(SystemLogEntry.restore(dt(rs, "created_at"), rs.getString("user_label"), rs.getString("action_text"),
                            rs.getString("ip_address"), LogStatus.valueOf(rs.getString("status"))));
                }
            }
        }
        return out;
    }

    // ================================================================== accounts
    // Accounts use plain INSERT (never "upsert"): the email column is UNIQUE, and an upsert on a duplicate
    // email would silently overwrite somebody else's account. A duplicate now fails with an error instead.

    /** Saves a new student account. */
    public void insertStudent(Student s) throws SQLException {
        exec("INSERT INTO students (id, name, email, password_hash, is_active, archived, year_level) VALUES (?, ?, ?, ?, ?, ?, ?)",
                s.getId(), s.getName(), s.getEmail(), s.getPassword(), s.isActive(), s.isArchived(), s.getYearLevel());
    }

    /** Saves the changed fields of a student (name, e-mail, password hash, flags, year level). */
    public void updateStudent(Student s) throws SQLException {
        exec("UPDATE students SET name = ?, email = ?, password_hash = ?, is_active = ?, archived = ?, year_level = ? WHERE id = ?",
                s.getName(), s.getEmail(), s.getPassword(), s.isActive(), s.isArchived(), s.getYearLevel(), s.getId());
    }

    /** Deletes a student account row (no screen offers this any more). */
    public void deleteStudent(String id) throws SQLException { exec("DELETE FROM students WHERE id = ?", id); }

    /** Saves a new staff account. */
    public void insertStaff(Staff s) throws SQLException {
        exec("INSERT INTO staff (id, name, email, password_hash, department, title, is_active, archived) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                s.getId(), s.getName(), s.getEmail(), s.getPassword(), s.getDepartment(), s.getTitle(), s.isActive(), s.isArchived());
    }

    /** Saves the changed fields of a staff account. */
    public void updateStaff(Staff s) throws SQLException {
        exec("UPDATE staff SET name = ?, email = ?, password_hash = ?, department = ?, title = ?, is_active = ?, archived = ? WHERE id = ?",
                s.getName(), s.getEmail(), s.getPassword(), s.getDepartment(), s.getTitle(), s.isActive(), s.isArchived(), s.getId());
    }

    /** Deletes a staff account row (no screen offers this any more). */
    public void deleteStaff(String id) throws SQLException { exec("DELETE FROM staff WHERE id = ?", id); }

    /** Saves a new administrator account. */
    public void insertAdmin(Admin a) throws SQLException {
        exec("INSERT INTO admins (id, name, email, password_hash, title, is_active, archived) VALUES (?, ?, ?, ?, ?, ?, ?)",
                a.getId(), a.getName(), a.getEmail(), a.getPassword(), a.getTitle(), a.isActive(), a.isArchived());
    }

    /** Saves the changed fields of an administrator account. */
    public void updateAdmin(Admin a) throws SQLException {
        exec("UPDATE admins SET name = ?, email = ?, password_hash = ?, title = ?, is_active = ?, archived = ? WHERE id = ?",
                a.getName(), a.getEmail(), a.getPassword(), a.getTitle(), a.isActive(), a.isArchived(), a.getId());
    }

    /** Deletes an administrator account row (no screen offers this any more). */
    public void deleteAdmin(String id) throws SQLException { exec("DELETE FROM admins WHERE id = ?", id); }

    // ================================================================== modules

    /** Insert-or-update by id (modules have no other unique column, so this cannot clobber another row). */
    public void saveModule(Module m) throws SQLException {
        exec("INSERT INTO modules (id, code, title, credits, location, collect_by, status, semester, department, stocks, deleted, year_level, slot_limit) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE code = VALUES(code), title = VALUES(title), credits = VALUES(credits), "
                        + "location = VALUES(location), collect_by = VALUES(collect_by), status = VALUES(status), "
                        + "semester = VALUES(semester), department = VALUES(department), stocks = VALUES(stocks), deleted = 0, "
                        + "year_level = VALUES(year_level), slot_limit = VALUES(slot_limit)",
                m.getId(), m.getCode(), m.getTitle(), m.getCredits(), m.getLocation(), m.getCollectBy(), m.getStatus(),
                m.getSemester(), m.getDepartment(), m.getStocks(), m.getYearLevel(), m.getSlotLimit());
    }

    /** "Delete" from the inventory. The row stays (flagged) so existing appointments keep their module. */
    public void removeModule(UUID id) throws SQLException {
        exec("UPDATE modules SET deleted = 1 WHERE id = ?", id);
    }

    // ================================================================== appointments + stock

    /** Inserts a new appointment or updates an existing one (INSERT ... ON DUPLICATE KEY UPDATE). */
    public void saveAppointment(Appointment a) throws SQLException {
        exec("INSERT INTO appointments (id, module_id, appt_at, status, officer, student_id, requested_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE module_id = VALUES(module_id), appt_at = VALUES(appt_at), "
                        + "status = VALUES(status), officer = VALUES(officer), student_id = VALUES(student_id)",
                a.getId(), a.getModule().getId(), a.getDateTime(), a.getStatus(), a.getOfficer(),
                a.getStudentId(), a.getRequestedAt());
    }

    /** Sets the archived flag of an appointment (kept for old data; no screen uses it any more). */
    public void setAppointmentArchived(UUID id, boolean archived) throws SQLException {
        exec("UPDATE appointments SET archived = ? WHERE id = ?", archived, id);
    }

    /** Deletes an appointment row (no screen offers this any more). */
    public void deleteAppointment(UUID id) throws SQLException {
        exec("DELETE FROM appointments WHERE id = ?", id);
    }

    /**
     * Saves (inserts or updates) an appointment ONLY IF the module still has room in that exact date + time
     * slot. The module row is locked first, so two computers booking the same module at the same moment
     * queue up here and the count below can never be raced past the limit.
     *
     * @return true when saved, false when the slot was already full (nothing is changed then)
     */
    public boolean saveAppointmentIfSlotFree(Appointment a, int slotLimit) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement lock = c.prepareStatement("SELECT id FROM modules WHERE id = ? FOR UPDATE")) {
                lock.setString(1, a.getModule().getId().toString());
                try (ResultSet rs = lock.executeQuery()) { rs.next(); }
            }
            int taken = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM appointments WHERE module_id = ? AND appt_at = ? "
                    + "AND status <> 'CANCELLED' AND id <> ?")) {
                ps.setString(1, a.getModule().getId().toString());
                ps.setObject(2, a.getDateTime());
                ps.setString(3, a.getId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) taken = rs.getInt(1);
                }
            }
            if (taken >= slotLimit) {
                c.rollback();
                return false;
            }
            saveAppointment(a);
            c.commit();
            return true;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) { }
            throw e;
        } finally {
            try { c.setAutoCommit(auto); } catch (SQLException ignored) { }
        }
    }

    /** Result codes of {@link #completePickup}. */
    public static final int PICKUP_OUT_OF_STOCK = -1, PICKUP_NOT_APPROVED = -2;

    /**
     * Marks an approved appointment as picked up AND takes one copy off the shelf in one transaction.
     * The stock is only reduced when a copy is really left ("stocks &gt; 0" inside the UPDATE), so when two
     * students were approved for the last copy, only the first one marked picked up gets it.
     *
     * @param onlyIfApproved true for staff (the appointment must be Approved); false for an admin override
     * @return the module's stock after the pickup (0 or more), {@link #PICKUP_OUT_OF_STOCK} when no copy
     *         was left, or {@link #PICKUP_NOT_APPROVED} when the appointment is no longer in the right
     *         state in the database (someone else already changed it). Nothing is changed for a negative result.
     */
    public int completePickup(Appointment a, boolean onlyIfApproved) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int moved = exec(onlyIfApproved
                    ? "UPDATE appointments SET status = 'COMPLETED' WHERE id = ? AND status = 'CONFIRMED'"
                    : "UPDATE appointments SET status = 'COMPLETED' WHERE id = ? AND status <> 'COMPLETED'", a.getId());
            if (moved == 0) {
                c.rollback();
                return PICKUP_NOT_APPROVED;
            }
            int taken = exec("UPDATE modules SET stocks = stocks - 1 WHERE id = ? AND stocks > 0", a.getModule().getId());
            if (taken == 0) {
                c.rollback();
                return PICKUP_OUT_OF_STOCK;
            }
            int stock = readStock(c, a.getModule().getId());
            c.commit();
            return stock;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) { }
            throw e;
        } finally {
            try { c.setAutoCommit(auto); } catch (SQLException ignored) { }
        }
    }

    /** Reads a module's current stock inside the running transaction. */
    private int readStock(Connection c, UUID moduleId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT stocks FROM modules WHERE id = ?")) {
            ps.setString(1, moduleId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Saves an appointment's new status and, in the same transaction, moves the module's stock by
     * {@code stockDelta} (never below 0). The change is done in SQL (stocks = stocks + delta), so two
     * computers handing out the same module can't overwrite each other's count. (Handing a copy OUT is
     * {@link #completePickup}; this one is for everything else, e.g. putting a copy back.)
     *
     * @return the module's stock after the change, or -1 when stockDelta was 0
     */
    public int saveAppointmentStatus(Appointment a, int stockDelta) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            exec("UPDATE appointments SET status = ?, officer = ? WHERE id = ?", a.getStatus(), a.getOfficer(), a.getId());
            int stock = -1;
            if (stockDelta != 0) {
                exec("UPDATE modules SET stocks = GREATEST(stocks + ?, 0) WHERE id = ?", stockDelta, a.getModule().getId());
                stock = readStock(c, a.getModule().getId());
            }
            c.commit();
            return stock;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) { }
            throw e;
        } finally {
            try { c.setAutoCommit(auto); } catch (SQLException ignored) { }
        }
    }

    // ================================================================== notifications / feedback

    /** Inserts a new notification or updates its read flag. */
    public void saveNotification(NotificationItem n) throws SQLException {
        exec("INSERT INTO notifications (id, student_id, title, message, created_at, is_read) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE title = VALUES(title), message = VALUES(message), is_read = VALUES(is_read)",
                n.getId(), n.getStudentId(), n.getTitle(), n.getMessage(), n.getTimestamp(), n.isRead());
    }

    /** Deletes one notification (the student's "Dismiss" button). */
    public void deleteNotification(UUID id) throws SQLException { exec("DELETE FROM notifications WHERE id = ?", id); }

    /** Saves a feedback entry. */
    public void saveFeedback(Feedback f) throws SQLException {
        exec("INSERT INTO feedback (id, student_id, category, rating, comment_text, submitted_at) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE category = VALUES(category), rating = VALUES(rating), comment_text = VALUES(comment_text)",
                f.getId(), f.getStudentId(), f.getCategory(), f.getRating(), f.getComment(), f.getSubmittedAt());
    }

    // ================================================================== student queries (tickets)

    /** Sets the archived flag of a ticket (kept for old data; no screen uses it any more). */
    public void setQueryArchived(java.util.UUID id, boolean archived) throws SQLException {
        exec("UPDATE student_queries SET archived = ? WHERE id = ?", archived, id);
    }

    /** Deletes a ticket row (no screen offers this any more). */
    public void deleteQuery(java.util.UUID id) throws SQLException {
        exec("DELETE FROM student_queries WHERE id = ?", id);
    }

    /** Saves a new student query. */
    public void insertQuery(StudentQuery q) throws SQLException {
        exec("INSERT INTO student_queries (id, ticket_number, student_id, subject_code, category, priority, status, "
                        + "message, submitted_at, answered_at, reply) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                q.getId(), q.getTicketNumber(), q.getStudentId(), q.getSubjectCode(), q.getCategory(),
                q.getPriority(), q.getStatus(), q.getMessage(), q.getSubmittedAt(), q.getAnsweredAt(), q.getReply());
    }

    /** Saves a ticket's new status, answered time and reply. */
    public void updateQuery(StudentQuery q) throws SQLException {
        exec("UPDATE student_queries SET status = ?, answered_at = ?, reply = ? WHERE id = ?",
                q.getStatus(), q.getAnsweredAt(), q.getReply(), q.getId());
    }

    /** Next free ticket number, taken from the database so several computers don't hand out the same one. */
    public int nextTicketNumber() throws SQLException {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(ticket_number), 1018) + 1 FROM student_queries")) {
            return rs.next() ? rs.getInt(1) : 1019;
        }
    }

    // ================================================================== activity log

    /** Appends one row to the system activity log. */
    public void insertLog(SystemLogEntry e) throws SQLException {
        exec("INSERT INTO system_logs (created_at, user_label, action_text, ip_address, status) VALUES (?, ?, ?, ?, ?)",
                e.getTimestamp(), e.getUser(), e.getAction(), e.getIpAddress(), e.getStatus());
    }

    // ================================================================== enrollment overrides
    // Manual exceptions on top of the normal year-level match -- see Student.isEnrolledIn(Module).
    // kind is either "EXTRA" (module added despite year-level mismatch) or "DROPPED" (module
    // hidden despite year-level match).

    /** Every override row in the database, as {studentId, moduleCode, kind}. DemoDataStore applies
     *  these onto the matching Student objects once they're loaded. */
    public List<String[]> loadEnrollmentOverrides() throws SQLException {
        List<String[]> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT student_id, module_code, kind FROM student_module_overrides")) {
            while (rs.next()) {
                out.add(new String[]{rs.getString("student_id"), rs.getString("module_code"), rs.getString("kind")});
            }
        }
        return out;
    }

    /** Stores one manual course-load exception for a student (kind = EXTRA or DROPPED). */
    public void addEnrollmentOverride(String studentId, String moduleCode, String kind) throws SQLException {
        exec("INSERT IGNORE INTO student_module_overrides (student_id, module_code, kind) VALUES (?, ?, ?)",
                studentId, moduleCode, kind);
    }

    /** Removes one manual course-load exception. */
    public void removeEnrollmentOverride(String studentId, String moduleCode, String kind) throws SQLException {
        exec("DELETE FROM student_module_overrides WHERE student_id = ? AND module_code = ? AND kind = ?",
                studentId, moduleCode, kind);
    }

    // ================================================================== app settings (key/value)

    /** Returns the stored value for this key, or fallback if it isn't set yet. */
    public String loadSetting(String key, String fallback) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT setting_value FROM app_settings WHERE setting_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : fallback;
            }
        }
    }

    /** Saves one system-wide setting, e.g. the current term. */
    public void saveSetting(String key, String value) throws SQLException {
        exec("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)",
                key, value);
    }

    // ================================================================== password reset codes

    /** Stores (replacing any earlier one) the hashed reset code for an account. kind is STUDENT / STAFF / ADMIN. */
    public void savePasswordReset(String kind, String accountId, String codeHash, LocalDateTime expiresAt) throws SQLException {
        exec("INSERT INTO password_resets (account_kind, account_id, code_hash, expires_at, attempts, created_at) VALUES (?, ?, ?, ?, 0, ?) "
                        + "ON DUPLICATE KEY UPDATE code_hash = VALUES(code_hash), expires_at = VALUES(expires_at), attempts = 0, created_at = VALUES(created_at)",
                kind, accountId, codeHash, expiresAt, LocalDateTime.now());
    }

    /** {codeHash, expiresAt (ISO), attempts, createdAt (ISO)} or null when there is no pending reset. */
    public String[] loadPasswordReset(String kind, String accountId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT code_hash, expires_at, attempts, created_at FROM password_resets "
                + "WHERE account_kind = ? AND account_id = ?")) {
            ps.setString(1, kind);
            ps.setString(2, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new String[]{rs.getString("code_hash"), rs.getObject("expires_at", LocalDateTime.class).toString(),
                        String.valueOf(rs.getInt("attempts")), rs.getObject("created_at", LocalDateTime.class).toString()};
            }
        }
    }

    /** Counts one more wrong reset code for an account. */
    public void addPasswordResetAttempt(String kind, String accountId) throws SQLException {
        exec("UPDATE password_resets SET attempts = attempts + 1 WHERE account_kind = ? AND account_id = ?", kind, accountId);
    }

    /** Deletes the pending reset code of an account (after success, expiry or too many tries). */
    public void deletePasswordReset(String kind, String accountId) throws SQLException {
        exec("DELETE FROM password_resets WHERE account_kind = ? AND account_id = ?", kind, accountId);
    }
}
