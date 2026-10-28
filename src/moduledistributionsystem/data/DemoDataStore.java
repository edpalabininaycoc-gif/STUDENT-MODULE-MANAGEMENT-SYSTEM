package moduledistributionsystem.data;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import moduledistributionsystem.model.Account;
import moduledistributionsystem.model.Admin;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.model.AuthResult;
import moduledistributionsystem.model.Feedback;
import moduledistributionsystem.model.LogStatus;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.model.ModuleStatus;
import moduledistributionsystem.model.NotificationItem;
import moduledistributionsystem.model.QueryPriority;
import moduledistributionsystem.model.QueryStatus;
import moduledistributionsystem.model.Role;
import moduledistributionsystem.model.Staff;
import moduledistributionsystem.model.Student;
import moduledistributionsystem.model.StudentQuery;
import moduledistributionsystem.model.SystemLogEntry;
import moduledistributionsystem.model.YearLevel;

/**
 * The program's "brain" and shared memory: it holds every list (accounts, modules, appointments,
 * notifications, queries, logs) and contains ALL the business rules. The screens never decide anything
 * themselves; they call methods here and show the answer.
 *
 * Whichever role is logged in (Student, Staff, Admin) reads and writes the same lists here.
 *
 * Two modes:
 * - Database mode (new DemoDataStore(database)): the lists are loaded from MySQL / MariaDB and every
 *   change is written back through the Database class, so nothing is lost when the app closes.
 * - Demo mode (new DemoDataStore()): sample data kept only in memory. Used automatically when the
 *   database can not be reached.
 *
 * How this file is organized (search for these section names):
 * 1. start-up: constructors, loading from the database, sample data, curriculum sync
 * 2. Authentication: login, lockout, logout, session check
 * 3. accounts: add / activate / deactivate / archive / change password
 * 4. per-student views and simple add / remove helpers
 * 5. booking rules: book, reschedule, cancel, approve, decline, pick up (slot limit + stock rules +
 * FIFO)
 * 6. modules + stock alerts
 * 7. student queries (tickets)
 * 8. Forgot password (e-mailed one-time code)
 *
 * How to customize: business rules (limits, messages, who may do what) are all here. Time slots live in
 * PickupSchedule, subjects in Curriculum, SQL in Database.
 */
public class DemoDataStore {
    private List<Student> studentAccounts = new ArrayList<>();
    private List<Staff> staffAccounts = new ArrayList<>();
    private List<Admin> adminAccounts = new ArrayList<>();
    /** The student who is signed in (null when nobody, or someone else, is signed in). */
    private Student currentStudent;
    /** The staff member who is signed in. */
    private Staff currentStaff;
    /** The administrator who is signed in. */
    private Admin currentAdmin;

    /**
     * The semester currently being distributed, system-wide ("Semester 1" or "Semester 2"). A module
     * shows on a student's Modules page only when its year level AND this semester match. The
     * administrator changes it in Manage Users > Current Term.
     */
    private String currentSemester = "Semester 1";
    /** The semester currently being distributed. */
    public String getCurrentSemester() { return currentSemester; }
    /** Changes the current term and saves it, so every computer sees the same one. */
    public void setCurrentSemester(String currentSemester) {
        this.currentSemester = currentSemester;
        persist("save the current semester setting", () -> db.saveSetting("current_semester", currentSemester));
    }

    /** Every module. The in-memory copy of the "modules" table. */
    private List<Module> modules = new ArrayList<>();
    /** Every appointment, oldest first (so list order is also arrival order). */
    private List<Appointment> appointments = new ArrayList<>();
    /** Every notification, newest first. */
    private List<NotificationItem> notifications = new ArrayList<>();
    /** Every feedback entry. */
    private List<Feedback> feedbacks = new ArrayList<>();
    /** Every student query (ticket). */
    private List<StudentQuery> queries = new ArrayList<>();
    /** The system activity log, newest first (at most LOG_LIMIT rows are kept in memory). */
    private List<SystemLogEntry> logs = new ArrayList<>();
    /** Next ticket number in demo mode (in database mode the database hands them out). */
    private int nextTicketNumber = 1019;

    // Demo apps don't have a real network connection to read an IP from -- this
    // placeholder is shown on every log row, same as a LAN-only kiosk machine would.
    private static final String DEMO_IP = "192.168.1.10";
    /** How many log rows are kept in memory / loaded from the database. */
    private static final int LOG_LIMIT = 1000;

    /** The database, or null in demo mode. Every "persist(...)" call does nothing when this is null. */
    private final Database db; // null = in-memory demo mode

    /** Called when a database write or read fails, so the UI can tell the user. */
    public interface DbErrorListener { void onError(String message, Exception cause); }

    /** Who to tell about database errors. By default they are printed to the console. */
    private DbErrorListener errorListener = (message, cause) -> {
        System.err.println(message);
        cause.printStackTrace();
    };

    /**
     * One database write, run inside persist(...) so any SQLException is reported instead of crashing a
     * screen.
     */
    private interface DbTask { void run() throws SQLException; }

    /** Demo mode: sample data kept only in memory (nothing is saved). */
    public DemoDataStore() {
        this.db = null;
        seedDemoData();
    }

    /**
     * Database mode. A brand-new (empty) database gets the sample accounts and the full curriculum
     * once; an existing one is loaded and then brought up to date with the curriculum (syncCurriculum).
     */
    public DemoDataStore(Database db) throws SQLException {
        this.db = db;
        if (db.isEmpty()) {
            seedDemoData();
            persistSeedData();
            currentStudent = null; // in database mode nobody is "logged in" until authenticate() succeeds
        } else {
            loadFromDatabase();
            syncCurriculum();
        }
    }

    /**
     * Brings a database that was created earlier in line with the registrar's official subject list
     * (Curriculum) WITHOUT wiping anything: a subject that's already there gets its title, units,
     * semester and year level corrected; a subject that's missing is added. Stock, status, pickup
     * location and deadline belong to Staff and are never touched. A subject Staff deleted on purpose
     * stays deleted. Runs once at start-up, and only ever writes when something actually differs.
     */
    private void syncCurriculum() throws SQLException {
        Map<UUID, Module> everything = new HashMap<>(); // includes deleted rows
        db.loadModules(everything);
        java.util.Set<String> knownCodes = new java.util.HashSet<>();
        for (Module m : everything.values()) knownCodes.add(m.getCode());

        boolean changed = false;
        int added = 0;
        for (Curriculum.Entry e : Curriculum.all()) {
            Module existing = null;
            for (Module m : modules) if (m.getCode().equalsIgnoreCase(e.code)) { existing = m; break; }
            if (existing != null) {
                boolean differs = !e.title.equals(existing.getTitle()) || e.units != existing.getCredits()
                        || !e.semester.equals(existing.getSemester()) || e.year != existing.getYearLevel()
                        || !Curriculum.DEPARTMENT.equals(existing.getDepartment());
                if (differs) {
                    existing.setTitle(e.title);
                    existing.setCredits(e.units);
                    existing.setSemester(e.semester);
                    existing.setYearLevel(e.year);
                    existing.setDepartment(Curriculum.DEPARTMENT);
                    db.saveModule(existing);
                    changed = true;
                }
            } else if (!knownCodes.contains(e.code)) {
                Module m = new Module(e.code, e.title, e.units, "Basement", LocalDate.now().plusDays(14),
                        ModuleStatus.READY, e.semester, Curriculum.DEPARTMENT, 100);
                m.setYearLevel(e.year);
                db.saveModule(m);
                modules.add(m);
                added++;
                changed = true;
            }
        }
        if (changed) {
            System.out.println("Curriculum sync: updated/added subjects (" + added + " new).");
            loadFromDatabase();
        }
    }

    /** True when data is really saved in a database (false in demo mode). */
    public boolean isUsingDatabase() { return db != null; }
    /** Text for the screens, e.g. "localhost:3306 / module_distribution". */
    public String databaseDescription() { return db == null ? "in-memory demo data" : db.describe(); }
    /** Sets who is told about database errors. */
    public void setDbErrorListener(DbErrorListener l) { if (l != null) this.errorListener = l; }

    /** Passes a database error to the listener. */
    private void reportDbError(String message, Exception e) { errorListener.onError(message, e); }

    /** Runs a database write; a failure is reported to the listener and never thrown into the UI. */
    private void persist(String what, DbTask task) {
        if (db == null) return;
        try {
            task.run();
        } catch (SQLException e) {
            reportDbError("Could not " + what + " in the database:\n" + e.getMessage(), e);
        }
    }

    /** True when the SQL error means "this id / e-mail already exists" (a unique key was violated). */
    private static boolean isDuplicate(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
                || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }

    // ---- Loading ----

    /**
     * Reads everything from the database into the in-memory lists. The lists are refilled IN PLACE (see
     * replace) so screens that already hold them see the new data.
     */
    private void loadFromDatabase() throws SQLException {
        List<Student> st = db.loadStudents();
        List<Staff> sf = db.loadStaff();
        List<Admin> ad = db.loadAdmins();
        Map<UUID, Module> allModules = new HashMap<>();
        List<Module> mods = db.loadModules(allModules);
        List<Appointment> appts = db.loadAppointments(allModules);
        List<NotificationItem> notes = db.loadNotifications();
        List<Feedback> fbs = db.loadFeedbacks();
        List<StudentQuery> qs = db.loadQueries();
        List<SystemLogEntry> lg = db.loadLogs(LOG_LIMIT);

        // Swap the contents in place so panels that already hold these lists see the new data.
        replace(studentAccounts, st);
        replace(staffAccounts, sf);
        replace(adminAccounts, ad);
        replace(modules, mods);
        replace(appointments, appts);
        replace(notifications, notes);
        replace(feedbacks, fbs);
        replace(queries, qs);
        replace(logs, lg);

        // Enrollment overrides live in their own table, keyed by student id -- reapply them onto
        // the freshly-loaded Student objects (loadStudents() only knows about year_level, not these).
        for (String[] row : db.loadEnrollmentOverrides()) {
            String studentId = row[0], moduleCode = row[1], kind = row[2];
            for (Student s : studentAccounts) {
                if (!s.getId().equals(studentId)) continue;
                if ("EXTRA".equals(kind)) s.enrollInModule(moduleCode);
                else if ("DROPPED".equals(kind)) s.dropModule(moduleCode);
                break;
            }
        }

        currentSemester = db.loadSetting("current_semester", currentSemester);

        // Objects were rebuilt, so point "who is logged in" at the fresh copies.
        if (currentStudent != null) {
            String id = currentStudent.getId();
            currentStudent = null;
            for (Student s : studentAccounts) if (s.getId().equals(id)) currentStudent = s;
        }
        if (currentStaff != null) {
            String id = currentStaff.getId();
            currentStaff = null;
            for (Staff s : staffAccounts) if (s.getId().equals(id)) currentStaff = s;
        }
        if (currentAdmin != null) {
            String id = currentAdmin.getId();
            currentAdmin = null;
            for (Admin a : adminAccounts) if (a.getId().equals(id)) currentAdmin = a;
        }

        int max = 1018;
        for (StudentQuery q : queries) max = Math.max(max, q.getTicketNumber());
        nextTicketNumber = max + 1;
    }

    /** Empties a list and fills it with the fresh items, keeping the same list object. */
    private static <T> void replace(List<T> target, List<T> fresh) {
        target.clear();
        target.addAll(fresh);
    }

    /**
     * Re-reads everything from the database so this computer sees changes other computers made
     * (called when the user switches screens and at login). Does nothing in demo mode.
     */
    public void reloadFromDatabase() {
        if (db == null) return;
        try {
            loadFromDatabase();
        } catch (SQLException e) {
            reportDbError("Could not refresh data from the database:\n" + e.getMessage(), e);
        }
    }

    /** Writes the sample data to a brand-new database (passwords are hashed first). */
    private void persistSeedData() throws SQLException {
        for (Student s : studentAccounts) { s.setPassword(PasswordHasher.hash(s.getPassword())); db.insertStudent(s); }
        for (Staff s : staffAccounts) { s.setPassword(PasswordHasher.hash(s.getPassword())); db.insertStaff(s); }
        for (Admin a : adminAccounts) { a.setPassword(PasswordHasher.hash(a.getPassword())); db.insertAdmin(a); }
        for (Module m : modules) db.saveModule(m);
        for (Appointment a : appointments) db.saveAppointment(a);
        for (NotificationItem n : notifications) db.saveNotification(n);
        for (Feedback f : feedbacks) db.saveFeedback(f);
        for (StudentQuery q : queries) db.insertQuery(q);
        for (SystemLogEntry e : logs) db.insertLog(e);
        db.saveSetting("current_semester", currentSemester);
    }

    // ---- Sample data ----

    /**
     * Creates the sample accounts, modules (from Curriculum), appointments, notifications and queries
     * used in demo mode and in a brand-new database.
     */
    private void seedDemoData() {
        // ---- Demo accounts ----
        Student edmund = new Student("02-2526-015176", "Edmund Jezer P. Labininay", "edmund@example.edu", "student123");
        edmund.setYearLevel(YearLevel.SECOND_YEAR);
        studentAccounts.add(edmund);
        currentStudent = edmund; // sensible default so panels have data even before a real login call

        Student kerby = new Student("02-2526-015177", "Kerby Lusterio", "kerby.lusterio@example.edu", "student123");
        kerby.setActive(false);
        kerby.setYearLevel(YearLevel.SECOND_YEAR);
        studentAccounts.add(kerby);

        // 1st Year demo account -- exists mainly to prove the year-level + current-semester
        // filtering actually works (log in as this account and Modules should show only the
        // 8 Year1/Sem1 subjects, not the Year1/Sem2 ones or the Year2 ones).
        Student maria = new Student("02-2627-020045", "Maria Santos", "maria.santos@example.edu", "student123");
        maria.setYearLevel(YearLevel.FIRST_YEAR);
        studentAccounts.add(maria);

        Staff mark = new Staff("STF-001", "Mark Sanchez", "mark.sanchez@staff.edu", "staff123", "Registrar Office", "Module Distributor");
        staffAccounts.add(mark);

        Admin admin = new Admin("ADM-001", "System Admin", "admin@smms.edu", "admin123", "Administrator");
        adminAccounts.add(admin);

        // ---- Modules: the registrar's official CITE / BSIT subject list (see Curriculum) ----
        // Networking 1 and Life and Works of Rizal start as "Pending" with no stock, so the demo
        // shows both a Ready and a Coming Soon state; everything else starts Ready with 100 copies.
        int n = 0;
        for (Curriculum.Entry e : Curriculum.all()) {
            boolean pending = e.code.equals("ITE292") || e.code.equals("HIS007");
            Module m = new Module(e.code, e.title, e.units, "Basement", LocalDate.now().plusDays(10 + (n % 7) * 2),
                    pending ? ModuleStatus.PENDING : ModuleStatus.READY, e.semester, Curriculum.DEPARTMENT, pending ? 0 : 100);
            m.setYearLevel(e.year);
            modules.add(m);
            n++;
        }

        // ---- Enrollment ----
        // Both demo students are BSIT-2 (see their IDs and yearLevel above), so Student.isEnrolledIn(Module)
        // auto-matches them into every module above -- no manual enrollInModule() calls needed for the
        // common case anymore. Only use enrollInModule()/unenrollFromModule() for actual exceptions
        // (a repeat student retaking an earlier-year subject, a cross-year elective, etc).

        // ---- Appointments ----
        Appointment a1 = new Appointment(modules.get(0), LocalDateTime.now().plusDays(4).withHour(10).withMinute(0), "Basement", "Mark Sanchez");
        a1.setStatus(AppointmentStatus.CONFIRMED);
        a1.setStudentName(edmund.getName());
        a1.setStudentId(edmund.getId());
        appointments.add(a1);

        Appointment a2 = new Appointment(modules.get(1), LocalDateTime.now().minusDays(10).withHour(17).withMinute(0), "Basement", "Ms. Rivera");
        a2.setStatus(AppointmentStatus.COMPLETED);
        a2.setStudentName(edmund.getName());
        a2.setStudentId(edmund.getId());
        appointments.add(a2);

        Appointment a3 = new Appointment(modules.get(1), LocalDateTime.now().plusDays(2).withHour(9).withMinute(30), "Basement", "Mark Sanchez");
        a3.setStatus(AppointmentStatus.PENDING);
        a3.setStudentName(edmund.getName());
        a3.setStudentId(edmund.getId());
        appointments.add(a3);

        // ---- Notifications ----
        notifications.add(new NotificationItem(edmund.getId(), "New Module Available — ITE031", "Data Structure & Algorithms is now available for booking. Collect before " + modules.get(0).getCollectBy()));
        notifications.add(new NotificationItem(edmund.getId(), "Pickup Reminder — ITE292", "You have not yet booked a pickup for Networking I. Deadline approaching."));
        notifications.add(new NotificationItem(edmund.getId(), "Appointment Confirmed", "Your appointment for ITE031 has been confirmed."));

        // ---- Feedback ----
        feedbacks.add(new Feedback(edmund.getId(), "Distribution Process", 5, "Smooth pickup process last semester."));

        // ---- Student queries (Inquiries Desk) ----
        queries.add(new StudentQuery(nextTicketNumber++, edmund.getId(), edmund.getName(), "ITE292", "Module Availability",
                QueryPriority.LOW, "Is Networking 1 still on track for the collect-by date shown?"));
        queries.add(new StudentQuery(nextTicketNumber++, edmund.getId(), edmund.getName(), "ITE031", "General Inquiry",
                QueryPriority.MEDIUM, "Can I bring a classmate's ID to pick up ITE031 on their behalf?"));
        queries.add(new StudentQuery(nextTicketNumber++, edmund.getId(), edmund.getName(), "GEN003", "Schedule Conflict",
                QueryPriority.HIGH, "My pickup slot for GEN003 conflicts with an exam. Can it be moved?"));

        // ---- System activity log (seeded history + every real login from here on) ----
        logs.add(new SystemLogEntry("Admin (administrator)", "System Backup Initiated", DEMO_IP, LogStatus.SUCCESS));
        logs.add(new SystemLogEntry(mark.getName() + " (staff)", "Module Created: ITE031 Data Structure", DEMO_IP, LogStatus.SUCCESS));
        logs.add(new SystemLogEntry(kerby.getName() + " (student)", "Failed Login Attempt", DEMO_IP, LogStatus.FAILED));
        logs.add(new SystemLogEntry(edmund.getName() + " (student)", "Student Portal Login", DEMO_IP, LogStatus.SUCCESS));
    }

    // ---- Authentication ----

    /** Why the last {@link #authenticate} call failed (so the login screen can say something more useful than "invalid"). */
    public enum AuthFailure { NONE, INVALID, INACTIVE, ARCHIVED, LOCKED }

    /** Wrong passwords in a row before a login is locked. */
    private static final int MAX_FAILED_LOGINS = 5;
    /** How long a locked login stays locked (milliseconds). */
    private static final long LOCK_MILLIS = 60_000;
    /** Why the last login failed, so the login screen can show the right message. */
    private AuthFailure lastAuthFailure = AuthFailure.NONE;
    /** Seconds left of the lock when the last failure was LOCKED. */
    private long lastLockSecondsLeft = 0;
    private final Map<String, int[]> failedLogins = new HashMap<>();   // identifier -> {count}
    private final Map<String, Long> lockedUntil = new HashMap<>();

    /** Why the last call to authenticate() failed (NONE after a success). */
    public AuthFailure getLastAuthFailure() { return lastAuthFailure; }
    /** Seconds until a locked login may be tried again. */
    public long getLastLockSecondsLeft() { return lastLockSecondsLeft; }

    /**
     * Checks a login. Students sign in with their School ID (or their e-mail); staff and admins with their e-mail.
     * The password is checked BEFORE the account state is revealed, so nobody learns an account is deactivated
     * without knowing its password. Five wrong passwords in a row lock that login for a minute.
     */
    public AuthResult authenticate(String identifier, String password) {
        reloadFromDatabase(); // pick up accounts other computers just added, changed or deactivated
        String trimmedId = identifier == null ? "" : identifier.trim();
        String key = trimmedId.toLowerCase(Locale.ROOT);
        lastAuthFailure = AuthFailure.INVALID;
        lastLockSecondsLeft = 0;

        Long until = lockedUntil.get(key);
        if (until != null) {
            if (until > System.currentTimeMillis()) {
                lastAuthFailure = AuthFailure.LOCKED;
                lastLockSecondsLeft = Math.max(1, (until - System.currentTimeMillis() + 999) / 1000);
                addLog(trimmedId, "Login Blocked \u2014 Too Many Failed Attempts", LogStatus.FAILED);
                return null;
            }
            lockedUntil.remove(key);
            failedLogins.remove(key);
        }

        // One loop for every kind of account: matchesLogin() and toAuthResult() are polymorphic, so each
        // subclass (Student / Staff / Admin) answers in its own way and no instanceof checks are needed here.
        for (Account acct : allAccounts()) {
            if (!acct.matchesLogin(trimmedId)) continue;
            if (!PasswordHasher.matches(password, acct.getPassword())) continue;
            String who = acct.getName() + " (" + roleLabel(acct) + ")";
            if (acct.isArchived()) return blocked(who, AuthFailure.ARCHIVED, "Login Blocked \u2014 Account Archived");
            if (!acct.isActive()) return blocked(who, AuthFailure.INACTIVE, "Login Blocked \u2014 Account Inactive");
            signIn(acct);
            loginSucceeded(key);
            addLog(who, acct.getRoleName() + " Portal Login", LogStatus.SUCCESS);
            return acct.toAuthResult();
        }

        int[] c = failedLogins.computeIfAbsent(key, k -> new int[1]);
        c[0]++;
        if (c[0] >= MAX_FAILED_LOGINS) {
            lockedUntil.put(key, System.currentTimeMillis() + LOCK_MILLIS);
            c[0] = 0;
        }
        addLog(trimmedId, "Failed Login Attempt", LogStatus.FAILED);
        return null;
    }

    /** Every account of every role, students first (the order in which a login is looked up). */
    private List<Account> allAccounts() {
        List<Account> all = new ArrayList<>();
        all.addAll(studentAccounts);
        all.addAll(staffAccounts);
        all.addAll(adminAccounts);
        return all;
    }

    /** Remembers who is signed in, in the field that matches the account's role. */
    private void signIn(Account acct) {
        switch (acct.getRole()) {
            case STUDENT: currentStudent = (Student) acct; break;
            case STAFF: currentStaff = (Staff) acct; break;
            default: currentAdmin = (Admin) acct; break;
        }
    }

    /** Records a refused login (log row + reason) and returns null. */
    private AuthResult blocked(String who, AuthFailure why, String logText) {
        lastAuthFailure = why;
        addLog(who, logText, LogStatus.FAILED);
        return null;
    }

    /** Clears the failed-attempt counter and any lock of this login. */
    private void loginSucceeded(String key) {
        lastAuthFailure = AuthFailure.NONE;
        failedLogins.remove(key);
        lockedUntil.remove(key);
    }

    /** Forget who is signed in (called when somebody logs out), so a stale session can never be reused. */
    public void logout() {
        currentStudent = null;
        currentStaff = null;
        currentAdmin = null;
    }

    /**
     * False when the signed-in account was deactivated, archived or removed by an administrator while the
     * window was open. The portals check this each time the user changes section and sign them out.
     */
    public boolean isSessionStillValid(Role role) {
        Account who = role == Role.STUDENT ? currentStudent : role == Role.STAFF ? currentStaff : role == Role.ADMIN ? currentAdmin : null;
        return who != null && who.canLogin();
    }

    /**
     * Adds one row to the system activity log (and saves it). Called for logins, failed logins, account
     * changes and password resets.
     */
    public void addLog(String user, String action, LogStatus status) {
        SystemLogEntry entry = new SystemLogEntry(user, action, DEMO_IP, status);
        logs.add(0, entry);
        persist("write the activity log", () -> db.insertLog(entry));
    }

    /** All student accounts (the live list). */
    public List<Student> getStudentAccounts() { return studentAccounts; }
    /** All staff accounts (the live list). */
    public List<Staff> getStaffAccounts() { return staffAccounts; }
    /** All administrator accounts (the live list). */
    public List<Admin> getAdminAccounts() { return adminAccounts; }

    // Passwords are hashed here, so the UI can keep passing plain text and nothing readable is ever stored.
    // In database mode a new account is only added to the lists if the database accepted it
    // (a duplicate ID or email is rejected, not merged into an existing account).
    // Each add method returns null on success, or a message saying why the account was not added.

    /** True if this e-mail already belongs to any student, staff or admin account (logins must stay unambiguous). */
    private boolean emailTaken(String email, Object ignore) {
        if (email == null) return false;
        for (Student s : studentAccounts) if (s != ignore && s.getEmail().equalsIgnoreCase(email)) return true;
        for (Staff s : staffAccounts) if (s != ignore && s.getEmail().equalsIgnoreCase(email)) return true;
        for (Admin a : adminAccounts) if (a != ignore && a.getEmail().equalsIgnoreCase(email)) return true;
        return false;
    }

    /** Simple format check: something@something.something, no spaces. */
    private static boolean looksLikeEmail(String email) {
        return email != null && email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    /**
     * Checks a new account's e-mail: valid format and not used by any other account. Returns null when
     * fine, otherwise the message to show.
     */
    private String checkNewAccount(String id, String email) {
        if (!looksLikeEmail(email)) return "Please enter a valid e-mail address (like name@school.edu).";
        if (emailTaken(email, null)) return "That e-mail address is already used by another account.";
        return null;
    }

    /**
     * Adds a student account (password is hashed here). Returns null on success, otherwise the reason
     * it was refused (duplicate School ID or e-mail, bad e-mail).
     */
    public String addStudentAccount(Student s) {
        String problem = checkNewAccount(s.getId(), s.getEmail());
        if (problem == null) for (Student o : studentAccounts) if (o.getId().equalsIgnoreCase(s.getId())) problem = "That Student ID already exists.";
        if (problem != null) return problem;
        s.setPassword(PasswordHasher.hash(s.getPassword()));
        if (db != null) {
            try { db.insertStudent(s); } catch (SQLException e) { reportAccountError(e); return "The database did not accept the new account."; }
        }
        studentAccounts.add(s);
        return null;
    }

    /** Adds the given module code to a student's course load, so it starts showing up on
     *  their Modules page. Used by the Admin "Enrollment" dialog (Manage Users) for the repeat-
     *  student / cross-year-elective case; the normal way a student gets a module is still the
     *  automatic year-level match. */
    public void addEnrollmentOverride(String studentId, String moduleCode) {
        Student s = findStudent(studentId);
        if (s == null) return;
        s.enrollInModule(moduleCode);
        persist("save enrollment override", () -> db.addEnrollmentOverride(studentId, moduleCode, "EXTRA"));
    }

    /** Removes a manual "extra" exception added above (does not affect the normal year-level
     *  match). */
    public void removeEnrollmentOverride(String studentId, String moduleCode) {
        Student s = findStudent(studentId);
        if (s == null) return;
        s.unenrollFromModule(moduleCode);
        persist("remove enrollment override", () -> db.removeEnrollmentOverride(studentId, moduleCode, "EXTRA"));
    }

    /** Hides a module from this student even though it matches their year level (e.g. already
     *  passed, or substituted for something else). */
    public void dropModuleForStudent(String studentId, String moduleCode) {
        Student s = findStudent(studentId);
        if (s == null) return;
        s.dropModule(moduleCode);
        persist("save dropped module", () -> db.addEnrollmentOverride(studentId, moduleCode, "DROPPED"));
    }

    /** Undoes dropModuleForStudent() -- the module goes back to following the normal match. */
    public void undropModuleForStudent(String studentId, String moduleCode) {
        Student s = findStudent(studentId);
        if (s == null) return;
        s.undropModule(moduleCode);
        persist("remove dropped module", () -> db.removeEnrollmentOverride(studentId, moduleCode, "DROPPED"));
    }

    /**
     * The modules this student can see right now: their enrolled modules (year-level match plus any
     * manual exceptions -- see Student.isEnrolledIn) that also belong to the CURRENT term. One rule,
     * used by the student's Modules page and by the Admin's "Enrolled Modules" count, so they always agree.
     */
    public List<Module> getCurrentModulesFor(Student student) {
        List<Module> mine = new ArrayList<>();
        if (student == null) return mine;
        for (Module m : modules) {
            if (!student.isEnrolledIn(m)) continue;
            if (m.getSemester() != null && !m.getSemester().equalsIgnoreCase(currentSemester)) continue;
            mine.add(m);
        }
        return mine;
    }

    /** Changes a student's year level (e.g. fixing an account that never had one, or promotion). */
    public void setStudentYearLevel(String studentId, YearLevel yearLevel) {
        Student s = findStudent(studentId);
        if (s == null) return;
        s.setYearLevel(yearLevel);
        persist("save the student's year level", () -> db.updateStudent(s));
    }

    /** Finds a student by School ID (null when none). */
    private Student findStudent(String studentId) {
        for (Student s : studentAccounts) if (s.getId().equals(studentId)) return s;
        return null;
    }

    /**
     * Adds a staff account (password is hashed here). Returns null on success, otherwise the reason it
     * was refused.
     */
    public String addStaffAccount(Staff s) {
        String problem = checkNewAccount(s.getId(), s.getEmail());
        if (problem == null) for (Staff o : staffAccounts) if (o.getId().equalsIgnoreCase(s.getId())) problem = "That Staff ID already exists.";
        if (problem != null) return problem;
        s.setPassword(PasswordHasher.hash(s.getPassword()));
        if (db != null) {
            try { db.insertStaff(s); } catch (SQLException e) { reportAccountError(e); return "The database did not accept the new account."; }
        }
        staffAccounts.add(s);
        return null;
    }

    /**
     * Adds an administrator account (password is hashed here). Returns null on success, otherwise the
     * reason it was refused.
     */
    public String addAdminAccount(Admin a) {
        String problem = checkNewAccount(a.getId(), a.getEmail());
        if (problem == null) for (Admin o : adminAccounts) if (o.getId().equalsIgnoreCase(a.getId())) problem = "That Admin ID already exists.";
        if (problem != null) return problem;
        a.setPassword(PasswordHasher.hash(a.getPassword()));
        if (db != null) {
            try { db.insertAdmin(a); } catch (SQLException e) { reportAccountError(e); return "The database did not accept the new account."; }
        }
        adminAccounts.add(a);
        return null;
    }

    /**
     * Tells the user why an account could not be saved (duplicate id / e-mail, or another database
     * error).
     */
    private void reportAccountError(SQLException e) {
        if (isDuplicate(e)) {
            reportDbError("That ID or email address is already used by another account, so it was not added.", e);
        } else {
            reportDbError("Could not save the new account in the database:\n" + e.getMessage(), e);
        }
    }

    /** True if this account object is the one currently signed in (an admin must not lock themself out). */
    public boolean isCurrentUser(Account account) {
        return account != null && (account == currentStudent || account == currentStaff || account == currentAdmin);
    }

    /** How many administrators can still sign in (used to protect the last one). */
    private int activeAdminCount() {
        int n = 0;
        for (Admin a : adminAccounts) if (a.canLogin()) n++;
        return n;
    }

    /** Why this account may not be removed / deactivated / archived right now, or null if it is fine. */
    public String whyCannotLockOut(Account account) {
        if (isCurrentUser(account)) return "You can't do that to the account you are signed in with.";
        if (account instanceof Admin && account.canLogin() && activeAdminCount() <= 1) {
            return "This is the last active administrator account. At least one must stay active.";
        }
        return null;
    }

    /** @return null when removed, otherwise the reason it was not */
    public String removeStudentAccount(Student s) {
        String why = whyCannotLockOut(s);
        if (why != null) return why;
        studentAccounts.remove(s);
        persist("delete the student account", () -> db.deleteStudent(s.getId()));
        return null;
    }

    /**
     * Deletes a staff account unless it is the signed-in account. Not offered by any screen any more
     * (delete is not allowed); kept for completeness. Returns null on success.
     */
    public String removeStaffAccount(Staff s) {
        String why = whyCannotLockOut(s);
        if (why != null) return why;
        staffAccounts.remove(s);
        persist("delete the staff account", () -> db.deleteStaff(s.getId()));
        return null;
    }

    /**
     * Deletes an administrator account unless it is the signed-in one or the last active one. Not
     * offered by any screen any more. Returns null on success.
     */
    public String removeAdminAccount(Admin a) {
        String why = whyCannotLockOut(a);
        if (why != null) return why;
        adminAccounts.remove(a);
        persist("delete the admin account", () -> db.deleteAdmin(a.getId()));
        return null;
    }

    /**
     * Saves one account to the database. This is the only place that has to know which table an account
     * belongs to; every other method just works with the {@link Account} base class.
     */
    private void saveAccount(Account account, String what) {
        if (account instanceof Student) persist(what, () -> db.updateStudent((Student) account));
        else if (account instanceof Staff) persist(what, () -> db.updateStaff((Staff) account));
        else if (account instanceof Admin) persist(what, () -> db.updateAdmin((Admin) account));
    }

    /** Activate / deactivate any account (Student, Staff or Admin) and save it. Deactivating un-archives nothing;
     *  activating an archived account also restores it. @return null on success, otherwise the reason it was refused. */
    public String setAccountActive(Account account, boolean active) {
        if (!active) {
            String why = whyCannotLockOut(account);
            if (why != null) return why;
        }
        account.setActive(active);
        if (active) account.setArchived(false);
        saveAccount(account, "update the account");
        return null;
    }

    /** Admin: archive a DEACTIVATED account (it stays in the database but is tucked away and cannot sign in),
     *  or restore it (it comes back deactivated until the admin activates it).
     *  @return null on success, otherwise the reason it was refused */
    public String setAccountArchived(Account account, boolean archived) {
        if (archived) {
            if (isCurrentUser(account)) return "You can't archive the account you are signed in with.";
            if (account.isActive()) return "Only a deactivated account can be archived. Deactivate it first.";
        }
        account.setArchived(archived);
        saveAccount(account, archived ? "archive the account" : "restore the account");
        return null;
    }

    /** Set a new password (plain text in, hashed value stored) for any account and save it. */
    public void changeAccountPassword(Account account, String plainPassword) {
        account.setPassword(PasswordHasher.hash(plainPassword));
        saveAccount(account, "update the password");
    }

    /** The signed-in student. */
    public Student getCurrentStudent() { return currentStudent; }
    /** The signed-in staff member. */
    public Staff getCurrentStaff() { return currentStaff; }
    /** The signed-in administrator. */
    public Admin getCurrentAdmin() { return currentAdmin; }

    /** All modules (the live list). */
    public List<Module> getModules() { return modules; }
    /** All appointments (the live list). */
    public List<Appointment> getAppointments() { return appointments; }
    /** All notifications (the live list). */
    public List<NotificationItem> getNotifications() { return notifications; }
    /** All feedback entries (the live list). */
    public List<Feedback> getFeedbacks() { return feedbacks; }
    /** All student queries (the live list). */
    public List<StudentQuery> getQueries() { return queries; }
    /** The system activity log (the live list, newest first). */
    public List<SystemLogEntry> getLogs() { return logs; }

    // ---- Per-student views ----
    // The lists above hold every record in the system (staff/admin need that).
    // Students should only ever see their own appointments/notifications/feedback,
    // so these filtered views are what student-facing panels should read from.
    public List<Appointment> getAppointmentsForStudent(String studentId) {
        List<Appointment> out = new ArrayList<>();
        for (Appointment a : appointments) if (studentId.equals(a.getStudentId())) out.add(a);
        return out;
    }
    /** The notifications of one student (newest first). */
    public List<NotificationItem> getNotificationsForStudent(String studentId) {
        List<NotificationItem> out = new ArrayList<>();
        for (NotificationItem n : notifications) if (studentId.equals(n.getStudentId())) out.add(n);
        return out;
    }
    /** The feedback entries of one student. */
    public List<Feedback> getFeedbacksForStudent(String studentId) {
        List<Feedback> out = new ArrayList<>();
        for (Feedback f : feedbacks) if (studentId.equals(f.getStudentId())) out.add(f);
        return out;
    }

    /**
     * Adds an already-built appointment (used by the demo data). Real bookings go through
     * bookAppointment().
     */
    public void addAppointment(Appointment appt) {
        appointments.add(appt);
        persist("save the appointment", () -> db.saveAppointment(appt));
    }

    /** Save changes made directly to an appointment (e.g. a rescheduled date/time). */
    public void saveAppointment(Appointment appt) {
        // If the screen held an older copy (the lists are re-read from the database when you switch
        // screens), make the list use this one so the change shows up right away.
        for (int i = 0; i < appointments.size(); i++) {
            Appointment inList = appointments.get(i);
            if (inList != appt && inList.getId().equals(appt.getId())) appointments.set(i, appt);
        }
        persist("save the appointment", () -> db.saveAppointment(appt));
    }

    /** Adds a notification for a student (newest first) and saves it. */
    public void addNotification(NotificationItem n) {
        notifications.add(0, n);
        persist("save the notification", () -> db.saveNotification(n));
    }

    /** Deletes a notification (the student's "Dismiss" button). */
    public void removeNotification(NotificationItem n) {
        notifications.remove(n);
        persist("delete the notification", () -> db.deleteNotification(n.getId()));
    }

    /** Marks a notification as read and saves it. */
    public void markNotificationRead(NotificationItem n) {
        n.markRead();
        persist("update the notification", () -> db.saveNotification(n));
    }

    /** Adds and saves a feedback entry. */
    public void addFeedback(Feedback f) {
        feedbacks.add(f);
        persist("save the feedback", () -> db.saveFeedback(f));
    }
    // ================================================================== booking rules

    /** Shown as the officer until a staff member approves the request (then it becomes that staff member's name). */
    public static final String DEFAULT_OFFICER = "Registrar Office";

    /** Finds a module by id (null when it no longer exists). */
    public Module findModule(UUID id) {
        for (Module m : modules) if (m.getId().equals(id)) return m;
        return null;
    }

    /**
     * Finds an appointment by id (null when it no longer exists). Screens keep ids, because lists are
     * re-read from the database.
     */
    public Appointment findAppointment(UUID id) {
        for (Appointment a : appointments) if (a.getId().equals(id)) return a;
        return null;
    }

    /** Why a NEW pickup cannot be booked for this module right now (not open, or out of stock), or null if it can. */
    public static String unavailableReason(Module m) {
        if (m == null) return "This module is no longer available.";
        if (m.getStatus() != ModuleStatus.READY) return m.getDisplayCode() + " is not open for pickup yet.";
        if (m.getStocks() <= 0) {
            return m.getDisplayCode() + " is out of stock right now. Modules are first come, first served \u2014 "
                    + "please check again when staff restock it.";
        }
        return null;
    }

    /** How many students already hold this module's date + time slot (anything not cancelled counts). */
    public int slotsTaken(Module m, LocalDateTime when, Appointment ignore) {
        int n = 0;
        for (Appointment a : appointments) {
            if (a.getModule() == null || !a.getModule().getId().equals(m.getId())) continue;
            if (ignore != null && a.getId().equals(ignore.getId())) continue;
            if (a.getStatus() == AppointmentStatus.CANCELLED) continue;
            if (a.getDateTime().equals(when)) n++;
        }
        return n;
    }

    /** Places still free in this module's date + time slot (the module's per-slot limit minus those taken). */
    public int slotsLeft(Module m, LocalDateTime when, Appointment ignore) {
        return Math.max(0, m.getSlotLimit() - slotsTaken(m, when, ignore));
    }

    /** The message shown when a time slot already has the module's maximum number of students. */
    private static String fullSlotMessage(Module m) {
        return "That time slot is already full (" + m.getSlotLimit() + " student" + (m.getSlotLimit() == 1 ? "" : "s")
                + " max per slot for " + m.getDisplayCode() + "). Please pick another time so the pickup desk doesn't get crowded.";
    }

    /** Formats a date and time for messages, e.g. "Oct 7, 2026 at 10:00 AM". */
    private static String whenText(LocalDateTime when) {
        return when.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH));
    }

    /**
     * A student books a pickup. Everything is re-checked here against the latest data (not just what the screen
     * showed): module open and in stock, part of the student's course load, no duplicate booking, a real future
     * slot before the collect-by date, and room left in that slot.
     *
     * @return null when booked, otherwise a message explaining why not
     */
    /**
     * Why this student cannot start a NEW booking for this module right now (module closed or out of stock, not on
     * their course load, already booked or already picked up), or null when they can. Checked before the
     * date/time dialog opens, and again inside {@link #bookAppointment}.
     */
    public String bookingBlockReason(Student student, Module module) {
        Student stu = student == null ? null : findStudent(student.getId());
        Module m = module == null ? null : findModule(module.getId());
        if (stu == null) return "Your account could not be found. Please sign in again.";
        if (!stu.isActive() || stu.isArchived()) return "Your account is not active.";
        String why = unavailableReason(m);
        if (why != null) return why;
        if (!getCurrentModulesFor(stu).contains(m)) return m.getDisplayCode() + " is not part of your course load this term.";
        for (Appointment a : appointments) {
            if (!stu.getId().equals(a.getStudentId()) || a.getModule() == null || !a.getModule().getId().equals(m.getId())) continue;
            if (a.getStatus() == AppointmentStatus.COMPLETED) return "You already picked up " + m.getDisplayCode() + ".";
            if (a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.CONFIRMED) {
                return "You already have an active appointment for " + m.getDisplayCode() + ". Use Reschedule to change its time instead.";
            }
        }
        return null;
    }

    /**
     * A student books a pickup. Everything is re-checked here against the latest data (not just what
     * the screen showed): the module is open and in stock, it is on the student's course load, there is
     * no duplicate booking, the slot is a real future time before the collect-by date, and there is
     * room left in that slot (slot limit). When a database is used the slot check is repeated inside a
     * transaction, so two computers can not take the last place.
     *
     * @return null when booked, otherwise the message to show the student
     */
    public String bookAppointment(Student student, Module module, LocalDateTime when) {
        reloadFromDatabase();
        String block = bookingBlockReason(student, module);
        if (block != null) return block;
        Student stu = findStudent(student.getId());
        Module m = findModule(module.getId());
        String timeProblem = PickupSchedule.problemWith(m, when);
        if (timeProblem != null) return timeProblem;
        if (slotsLeft(m, when, null) <= 0) return fullSlotMessage(m);

        Appointment appt = new Appointment(m, when, m.getLocation(), DEFAULT_OFFICER);
        appt.setStudentName(stu.getName());
        appt.setStudentId(stu.getId());
        if (db != null) {
            try {
                if (!db.saveAppointmentIfSlotFree(appt, m.getSlotLimit())) return fullSlotMessage(m);
            } catch (SQLException e) {
                reportDbError("Could not save the appointment in the database:\n" + e.getMessage(), e);
                return "Your appointment could not be saved. Please try again.";
            }
        }
        appointments.add(appt);
        addNotification(new NotificationItem(stu.getId(), "Appointment Requested",
                "You requested a pickup for " + m.getDisplayCode() + " on " + whenText(when) + ". Staff will confirm shortly."));
        return null;
    }

    /**
     * A student changes the time of a Pending / Approved pickup (it goes back to Pending for staff to re-approve).
     * Refused while the module is unavailable or out of stock, and when the new slot is full.
     *
     * @return null when rescheduled, otherwise a message explaining why not
     */
    public String rescheduleAppointment(Appointment appt, LocalDateTime when) {
        reloadFromDatabase();
        Appointment a = appt == null ? null : findAppointment(appt.getId());
        if (a == null) return "That appointment no longer exists.";
        if (a.getStatus() != AppointmentStatus.PENDING && a.getStatus() != AppointmentStatus.CONFIRMED) {
            return "Only pending or approved appointments can be rescheduled.";
        }
        Module m = a.getModule();
        if (m == null || !modules.contains(m)) return "This module was removed from the inventory, so the appointment can't be rescheduled.";
        String why = unavailableReason(m);
        if (why != null) return "Rescheduling is disabled for now. " + why;
        String timeProblem = PickupSchedule.problemWith(m, when);
        if (timeProblem != null) return timeProblem;
        if (slotsLeft(m, when, a) <= 0) return fullSlotMessage(m);

        LocalDateTime oldTime = a.getDateTime();
        AppointmentStatus oldStatus = a.getStatus();
        a.setDateTime(when);
        a.setStatus(AppointmentStatus.PENDING);
        if (db != null) {
            try {
                if (!db.saveAppointmentIfSlotFree(a, m.getSlotLimit())) {
                    a.setDateTime(oldTime);
                    a.setStatus(oldStatus);
                    return fullSlotMessage(m);
                }
            } catch (SQLException e) {
                a.setDateTime(oldTime);
                a.setStatus(oldStatus);
                reportDbError("Could not save the appointment in the database:\n" + e.getMessage(), e);
                return "Your appointment could not be changed. Please try again.";
            }
        }
        addNotification(new NotificationItem(a.getStudentId(), "Appointment Rescheduled",
                "Your pickup for " + m.getDisplayCode() + " was moved to " + whenText(when) + ". It is waiting for staff to confirm again."));
        return null;
    }

    /** A student cancels their own Pending / Approved pickup. @return false if it can't be cancelled any more */
    public boolean cancelAppointment(Appointment appt) {
        Appointment a = appt == null ? null : findAppointment(appt.getId());
        if (a == null || (a.getStatus() != AppointmentStatus.PENDING && a.getStatus() != AppointmentStatus.CONFIRMED)) return false;
        if (!updateAppointmentStatus(a, AppointmentStatus.CANCELLED)) return false;
        addNotification(new NotificationItem(a.getStudentId(), "Appointment Cancelled",
                "Your appointment for " + a.getModule().getDisplayCode() + " was cancelled."));
        return true;
    }

    // ================================================================== staff / admin appointment actions

    /** Staff: Pending -> Approved. The approving staff member becomes the student's officer. @return false if it wasn't Pending */
    public boolean approveAppointment(Appointment a, Staff by) {
        if (a == null || a.getStatus() != AppointmentStatus.PENDING) return false;
        if (by != null) a.setOfficer(by.getName());
        if (!updateAppointmentStatus(a, AppointmentStatus.CONFIRMED)) return false;
        addNotification(new NotificationItem(a.getStudentId(), "Appointment Confirmed",
                "Your appointment for " + a.getModule().getDisplayCode() + " on " + whenText(a.getDateTime()) + " has been confirmed."));
        return true;
    }

    /** Staff: Pending / Approved -> Declined. A pickup already handed out can't be declined. @return false if it can't be */
    public boolean declineAppointment(Appointment a) {
        if (a == null || (a.getStatus() != AppointmentStatus.PENDING && a.getStatus() != AppointmentStatus.CONFIRMED)) return false;
        if (!updateAppointmentStatus(a, AppointmentStatus.CANCELLED)) return false;
        addNotification(new NotificationItem(a.getStudentId(), "Appointment Declined",
                "Your appointment for " + a.getModule().getDisplayCode() + " could not be accommodated. Please rebook."));
        return true;
    }

    /** Result of handing a copy to a student. */
    public enum PickupResult { OK, OUT_OF_STOCK, NOT_APPROVED, ERROR }

    /**
     * Staff: the student collected the module (Approved -> Picked Up) and one copy comes off the shelf.
     * If the shelf is empty nothing changes and OUT_OF_STOCK is returned, so when two students were approved for
     * the last copy only the one marked picked up first gets it.
     */
    public PickupResult markPickedUp(Appointment a) {
        PickupResult r = completePickup(a, true);
        if (r == PickupResult.OK) {
            addNotification(new NotificationItem(a.getStudentId(), "Appointment Completed",
                    "Your pickup for " + a.getModule().getDisplayCode() + " was marked complete."));
        }
        return r;
    }

    /**
     * The line of APPROVED students waiting to collect one module, oldest request first (FIFO).
     * The student at the front of the line is the next one who should be served.
     */
    public FifoQueue<Appointment> pickupQueueFor(Module m) {
        List<Appointment> waiting = new ArrayList<>();
        for (Appointment a : appointments) {
            if (a.getModule() != null && a.getModule().getId().equals(m.getId()) && a.getStatus() == AppointmentStatus.CONFIRMED) waiting.add(a);
        }
        waiting.sort(Comparator.comparing(Appointment::getRequestedAt)); // stable: equal times keep their original order
        FifoQueue<Appointment> line = new FifoQueue<>();
        for (Appointment a : waiting) line.enqueue(a);
        return line;
    }

    /**
     * Marks several appointments as picked up in FIRST COME, FIRST SERVED order: they are put into a
     * {@link FifoQueue} sorted by the time each student asked, then served one at a time. When there are fewer
     * copies than students, the earliest requests get the copies and the rest get OUT_OF_STOCK.
     *
     * @return the result of every appointment, in the order they were served (a LinkedHashMap keeps that order)
     */
    public Map<Appointment, PickupResult> markPickedUpInOrder(List<Appointment> selected) {
        List<Appointment> ordered = new ArrayList<>(selected);
        ordered.sort(Comparator.comparing(Appointment::getRequestedAt));
        FifoQueue<Appointment> line = new FifoQueue<>();
        for (Appointment a : ordered) line.enqueue(a);

        Map<Appointment, PickupResult> results = new java.util.LinkedHashMap<>();
        while (!line.isEmpty()) {
            Appointment next = line.dequeue();       // the student who has waited longest
            results.put(next, markPickedUp(next));
        }
        return results;
    }

    /**
     * The one place where a copy of a module is really handed out: checks the appointment's state,
     * takes ONE copy off the stock (refusing when none is left, so stock never goes below zero), marks
     * the appointment COMPLETED, and sends stock alerts if the module just ran out. In database mode
     * the status change and the stock change happen in one transaction.
     *
     * @param onlyIfApproved true for staff (the appointment must be Approved); false for an
     *     administrator override
     */
    private PickupResult completePickup(Appointment a, boolean onlyIfApproved) {
        if (a == null) return PickupResult.ERROR;
        if (a.getStatus() == AppointmentStatus.COMPLETED || (onlyIfApproved && a.getStatus() != AppointmentStatus.CONFIRMED)) {
            return PickupResult.NOT_APPROVED;
        }
        Module m = a.getModule();
        boolean wasBookable = m != null && m.isBookable();
        if (db == null) {
            if (m != null && m.getStocks() <= 0) return PickupResult.OUT_OF_STOCK;
            if (m != null) m.setStocks(m.getStocks() - 1);
        } else {
            try {
                int r = db.completePickup(a, onlyIfApproved);
                if (r == Database.PICKUP_OUT_OF_STOCK) {
                    if (m != null) { boolean was = m.isBookable(); m.setStocks(0); availabilityChanged(m, was); }
                    return PickupResult.OUT_OF_STOCK;
                }
                if (r == Database.PICKUP_NOT_APPROVED) return PickupResult.NOT_APPROVED;
                if (m != null) m.setStocks(r);
            } catch (SQLException e) {
                reportDbError("Could not save the pickup in the database:\n" + e.getMessage(), e);
                return PickupResult.ERROR;
            }
        }
        a.setStatus(AppointmentStatus.COMPLETED);
        availabilityChanged(m, wasBookable);
        return PickupResult.OK;
    }

    /**
     * Single place where an appointment's status changes, so module stock stays correct no matter who changes it.
     * Entering COMPLETED hands out a copy (refused, returning false, when the module has none left);
     * leaving COMPLETED (admin correcting a mistake) puts that copy back.
     *
     * @return true when the status was changed (or already was that status)
     */
    public boolean updateAppointmentStatus(Appointment a, AppointmentStatus status) {
        AppointmentStatus old = a.getStatus();
        if (old == status) return true;
        if (status == AppointmentStatus.COMPLETED) return completePickup(a, false) == PickupResult.OK;

        Module m = a.getModule();
        boolean wasBookable = m != null && m.isBookable();
        int delta = (m != null && old == AppointmentStatus.COMPLETED) ? 1 : 0;
        a.setStatus(status);
        if (db == null) {
            if (delta != 0) m.setStocks(m.getStocks() + delta);
        } else {
            try {
                // Status and stock are saved in one transaction; the stock is changed in SQL, so two
                // computers handing out the same module at once can't overwrite each other's count.
                int stock = db.saveAppointmentStatus(a, delta);
                if (m != null && stock >= 0) m.setStocks(stock);
            } catch (SQLException e) {
                a.setStatus(old); // the database rolled back, so keep the screen in line with it
                reportDbError("Could not save the appointment status in the database:\n" + e.getMessage(), e);
                return false;
            }
        }
        availabilityChanged(m, wasBookable);
        return true;
    }

    /**
     * Moves an appointment into (or back out of) the archive. Not used by any screen any more (archive
     * and delete are not allowed); kept so older data and code still work.
     */
    public boolean setAppointmentArchived(Appointment a, boolean archived) {
        if (a.isArchived() == archived) return false;
        if (archived && a.getStatus() != AppointmentStatus.COMPLETED && a.getStatus() != AppointmentStatus.CANCELLED) return false;
        a.setArchived(archived);
        persist(archived ? "archive the appointment" : "restore the appointment", () -> db.setAppointmentArchived(a.getId(), archived));
        return true;
    }

    /**
     * Deletes an appointment. Not used by any screen any more (delete is not allowed); kept for
     * completeness.
     */
    public void deleteAppointment(Appointment a) {
        appointments.remove(a);
        persist("delete the appointment", () -> db.deleteAppointment(a.getId()));
    }

    // ================================================================== modules + stock alerts

    /** Students (active, on this module's course load this term) who have NOT collected the module yet. */
    private List<Student> studentsWhoStillNeed(Module m) {
        List<Student> out = new ArrayList<>();
        for (Student s : studentAccounts) {
            if (!s.isActive() || s.isArchived() || !getCurrentModulesFor(s).contains(m)) continue;
            boolean collected = false;
            for (Appointment a : appointments) {
                if (s.getId().equals(a.getStudentId()) && a.getModule() != null && a.getModule().getId().equals(m.getId())
                        && a.getStatus() == AppointmentStatus.COMPLETED) { collected = true; break; }
            }
            if (!collected) out.add(s);
        }
        return out;
    }

    /**
     * Call after anything that may have changed whether a module can be booked (READY and in stock).
     * When it just ran out (or was put on hold) the students who still need it are notified; when it
     * becomes available again they are told that too.
     */
    private void availabilityChanged(Module m, boolean wasBookable) {
        if (m == null || !modules.contains(m)) return;
        boolean now = m.isBookable();
        if (wasBookable == now) return;
        String code = m.getDisplayCode();
        for (Student s : studentsWhoStillNeed(m)) {
            if (!now) {
                boolean outOfStock = m.getStatus() == ModuleStatus.READY && m.getStocks() <= 0;
                addNotification(new NotificationItem(s.getId(),
                        (outOfStock ? "Module Out of Stock \u2014 " : "Module Unavailable \u2014 ") + code,
                        m.getTitle() + (outOfStock
                                ? " is out of stock. Pickups are paused until staff restock it, and rescheduling is disabled meanwhile. "
                                  + "We'll notify you when it is available again."
                                : " is not open for pickup right now, so rescheduling is disabled. We'll notify you when it opens again.")));
            } else {
                addNotification(new NotificationItem(s.getId(), "Module Available \u2014 " + code,
                        m.getTitle() + " is available for pickup (" + m.getStocks() + " in stock). Book a time slot before "
                                + m.getCollectBy() + "."));
            }
        }
    }

    /** Lowercase/uppercase and spaces don't make a different module: "ite 031" and "ITE031" are the same code. */
    public static String normalizeModuleCode(String code) {
        return code == null ? "" : code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** Why this code can't be used for a new / edited module (already taken), or null if it is free. */
    public String moduleCodeProblem(String code, Module self) {
        String norm = normalizeModuleCode(code);
        if (norm.isEmpty()) return "Module code is required.";
        for (Module m : modules) {
            if (m != self && normalizeModuleCode(m.getCode()).equals(norm)) {
                return "A module with the code " + m.getDisplayCode() + " is already in the inventory.";
            }
        }
        return null;
    }

    /** @return null when added, otherwise why not */
    public String addModule(Module m) {
        m.setCode(normalizeModuleCode(m.getCode()));
        String problem = moduleCodeProblem(m.getCode(), m);
        if (problem != null) return problem;
        modules.add(m);
        persist("save the module", () -> db.saveModule(m));
        availabilityChanged(m, false);
        return null;
    }

    /** Save changes made directly to a module (edited details or a corrected stock count), without any alerts. */
    public void saveModule(Module m) {
        persist("save the module", () -> db.saveModule(m));
    }

    /**
     * Save a module after the staff edited it. {@code wasBookable} is what {@code m.isBookable()} was BEFORE the
     * edit was applied, so students can be told if it just ran out or came back.
     * @return null when saved, otherwise why not
     */
    public String saveModuleEdit(Module m, boolean wasBookable) {
        m.setCode(normalizeModuleCode(m.getCode()));
        String problem = moduleCodeProblem(m.getCode(), m);
        if (problem != null) return problem;
        persist("save the module", () -> db.saveModule(m));
        availabilityChanged(m, wasBookable);
        return null;
    }

    /** Removes a module from the inventory. Its Pending / Approved appointments are declined (students are told).
     *  @return how many appointments were declined */
    public int removeModule(Module m) {
        int declined = 0;
        for (Appointment a : new ArrayList<>(appointments)) {
            if (a.getModule() == null || !a.getModule().getId().equals(m.getId())) continue;
            if (a.getStatus() != AppointmentStatus.PENDING && a.getStatus() != AppointmentStatus.CONFIRMED) continue;
            if (updateAppointmentStatus(a, AppointmentStatus.CANCELLED)) {
                addNotification(new NotificationItem(a.getStudentId(), "Appointment Declined",
                        "Your appointment for " + m.getDisplayCode() + " was cancelled because the module was removed from the inventory."));
                declined++;
            }
        }
        modules.remove(m);
        persist("remove the module", () -> db.removeModule(m.getId()));
        return declined;
    }

    /** Adds and saves a new student query. */
    public void addQuery(StudentQuery q) {
        queries.add(q);
        persist("save the query", () -> db.insertQuery(q));
    }

    /** Save a reply / status change made directly to a ticket. */
    public void saveQuery(StudentQuery q) {
        persist("save the query", () -> db.updateQuery(q));
    }

    /**
     * Archive / restore a ticket. Not used by any screen any more (archive and delete are not allowed);
     * kept so older data and code still work.
     */
    public boolean setQueryArchived(StudentQuery q, boolean archived) {
        if (q.isArchived() == archived) return false;
        if (archived && q.getStatus() != QueryStatus.ANSWERED) return false;
        q.setArchived(archived);
        persist(archived ? "archive the ticket" : "restore the ticket", () -> db.setQueryArchived(q.getId(), archived));
        return true;
    }

    /** Deletes a ticket. Not used by any screen any more (delete is not allowed); kept for completeness. */
    public void deleteQuery(StudentQuery q) {
        queries.remove(q);
        persist("delete the ticket", () -> db.deleteQuery(q.getId()));
    }

    /**
     * The next free ticket number (from the database when connected, so two computers never reuse a
     * number).
     */
    public int nextTicketNumber() {
        if (db != null) {
            try {
                int n = db.nextTicketNumber();
                nextTicketNumber = Math.max(nextTicketNumber, n + 1);
                return n;
            } catch (SQLException e) {
                reportDbError("Could not read the next ticket number from the database:\n" + e.getMessage(), e);
            }
        }
        return nextTicketNumber++;
    }

    // ================================================================== Reports (admin)

    /**
     * Runs one of the administrator's reports. With a database it is a real multi-table SQL query (JOIN / GROUP BY,
     * see {@link ReportKind#sql()}); in demo mode (no database) the same numbers are computed from the in-memory
     * lists, so the page works either way.
     */
    public Report runReport(ReportKind kind) {
        if (db != null) {
            try {
                return db.runReport(kind);
            } catch (SQLException e) {
                reportDbError("Could not run the report \"" + kind.label() + "\":\n" + e.getMessage(), e);
                return new Report(kind, new ArrayList<>(), true);
            }
        }
        return buildReportInMemory(kind);
    }

    /** Counters of one module for the "Appointments by Module" report. */
    private static final class ModuleCount {
        final Module module;
        long total, pending, approved, pickedUp, declined;

        ModuleCount(Module module) { this.module = module; }
    }

    /** The same four reports as the SQL queries, computed from the lists in memory (demo mode). */
    private Report buildReportInMemory(ReportKind kind) {
        List<String[]> rows = new ArrayList<>();
        switch (kind) {
            case APPOINTMENTS_BY_MODULE: {
                Map<UUID, ModuleCount> counts = new java.util.LinkedHashMap<>();
                for (Module m : modules) counts.put(m.getId(), new ModuleCount(m));
                for (Appointment a : appointments) {
                    ModuleCount c = a.getModule() == null ? null : counts.get(a.getModule().getId());
                    if (c == null) continue;
                    c.total++;
                    switch (a.getStatus()) {
                        case PENDING: c.pending++; break;
                        case CONFIRMED: c.approved++; break;
                        case COMPLETED: c.pickedUp++; break;
                        default: c.declined++; break;
                    }
                }
                List<ModuleCount> sorted = new ArrayList<>(counts.values());
                sorted.sort(Comparator.<ModuleCount>comparingLong(c -> -c.total).thenComparing(c -> c.module.getCode()));
                for (ModuleCount c : sorted) {
                    rows.add(new String[]{c.module.getDisplayCode(), c.module.getTitle(), c.module.getSemester(),
                            String.valueOf(c.module.getStocks()), String.valueOf(c.total), String.valueOf(c.pending),
                            String.valueOf(c.approved), String.valueOf(c.pickedUp), String.valueOf(c.declined)});
                }
                break;
            }
            case APPOINTMENT_DETAILS: {
                List<Appointment> sorted = new ArrayList<>(appointments);
                sorted.sort(Comparator.comparing(Appointment::getDateTime).reversed().thenComparing(Appointment::getStudentName));
                for (Appointment a : sorted) {
                    Student s = findStudent(a.getStudentId());
                    if (s == null || a.getModule() == null) continue;      // INNER JOIN: both sides must exist
                    rows.add(new String[]{s.getName(), s.getId(), Report.yearText(s.getYearLevel()), a.getModule().getDisplayCode(),
                            a.getModule().getTitle(), Report.whenText(a.getDateTime()), Report.statusText(a.getStatus()), a.getOfficer()});
                }
                break;
            }
            case BUSIEST_SLOTS: {
                Map<String, Set<String>> studentsBySlot = new HashMap<>();
                Map<String, Integer> bookingsBySlot = new HashMap<>();
                for (Appointment a : appointments) {
                    if (a.getStatus() == AppointmentStatus.CANCELLED) continue;
                    String slot = String.format("%02d:%02d", a.getDateTime().getHour(), a.getDateTime().getMinute());
                    bookingsBySlot.merge(slot, 1, Integer::sum);
                    studentsBySlot.computeIfAbsent(slot, k -> new java.util.HashSet<>()).add(a.getStudentId());
                }
                List<String> slots = new ArrayList<>(bookingsBySlot.keySet());
                slots.sort(Comparator.<String>comparingInt(sl -> -bookingsBySlot.get(sl)).thenComparing(sl -> sl));
                for (String slot : slots) {
                    rows.add(new String[]{Report.slotText(slot), String.valueOf(bookingsBySlot.get(slot)), String.valueOf(studentsBySlot.get(slot).size())});
                }
                break;
            }
            default: { // STUDENTS_BY_YEAR
                Map<YearLevel, long[]> byYear = new java.util.LinkedHashMap<>();   // {students, appointments, picked up}
                byYear.put(null, new long[3]);
                for (YearLevel y : YearLevel.values()) byYear.put(y, new long[3]);
                for (Student s : studentAccounts) {
                    long[] c = byYear.get(s.getYearLevel());
                    c[0]++;
                    for (Appointment a : appointments) {
                        if (!s.getId().equals(a.getStudentId())) continue;
                        c[1]++;
                        if (a.getStatus() == AppointmentStatus.COMPLETED) c[2]++;
                    }
                }
                for (Map.Entry<YearLevel, long[]> e : byYear.entrySet()) {
                    if (e.getValue()[0] == 0) continue;                       // a year level nobody belongs to has no row (like GROUP BY)
                    rows.add(new String[]{Report.yearText(e.getKey()), String.valueOf(e.getValue()[0]), String.valueOf(e.getValue()[1]),
                            String.valueOf(e.getValue()[2])});
                }
                break;
            }
        }
        return new Report(kind, rows, false);
    }

    // ================================================================== Forgot password (e-mailed one-time code)

    /** Shortest password a person may choose for themself when resetting. */
    public static final int MIN_PASSWORD_LENGTH = 6;
    /**
     * Reset-code rules: it works for 15 minutes, allows 5 wrong tries, and a new code can be asked only
     * every 60 seconds.
     */
    private static final int RESET_CODE_MINUTES = 15, RESET_MAX_ATTEMPTS = 5, RESET_COOLDOWN_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, String[]> memoryResets = new HashMap<>(); // demo mode: kind|id -> {hash, expiresAt, attempts, createdAt}
    /** How reset e-mails are sent. Created on first use from the smtp.* settings; tests can replace it. */
    private MailSender mailer;

    /** Replace the mail sender (tests use a fake one). By default e-mail goes out through the smtp.* settings of db.properties. */
    public void setMailSender(MailSender mailer) { this.mailer = mailer; }

    /** The mail sender (an SmtpMailer built from db.properties unless one was set with setMailSender). */
    public MailSender getMailSender() {
        if (mailer == null) mailer = new SmtpMailer(MailConfig.load());
        return mailer;
    }

    /** What the screen must do after asking for a reset code. {@code error} is set when nothing was sent and the user must be told why. */
    public static final class PendingReset {
        public final String error;       // null = carry on (the screen shows a neutral "if the account exists, a code was sent")
        public final boolean mustSend;   // false when no account matched (nothing to send, but the screen must not say so)
        final String kind, accountId, to, subject, body;

        /** Use failed(...) / nothingToSend() or DemoDataStore.prepareResetRequest to create one. */
        private PendingReset(String error, boolean mustSend, String kind, String accountId, String to, String subject, String body) {
            this.error = error; this.mustSend = mustSend; this.kind = kind; this.accountId = accountId;
            this.to = to; this.subject = subject; this.body = body;
        }
        /** A request that can not continue: the message tells the user why. */
        static PendingReset failed(String error) { return new PendingReset(error, false, null, null, null, null, null); }
        /**
         * No account matched. Looks like success to the user so nobody can find out which accounts
         * exist.
         */
        static PendingReset nothingToSend() { return new PendingReset(null, false, null, null, null, null, null); }

        /** Hand the e-mail to the mail server. Call from a background thread (it waits on the network). */
        public void deliver(MailSender sender) throws Exception { sender.send(to, subject, body); }
    }

    /** Finds the account a person means by what they typed on the login / reset screen (School ID or e-mail). */
    private Account findAccountByLogin(String identifier) {
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty()) return null;
        for (Account acct : allAccounts()) if (acct.matchesLogin(id)) return acct;
        return null;
    }

    /** The word used in log lines for this account's role ("student", "staff", "administrator"). */
    private static String roleLabel(Account a) { return a instanceof Admin ? "administrator" : a.getRoleName().toLowerCase(java.util.Locale.ROOT); }

    /** Reads the pending reset code of an account (database or, in demo mode, memory). */
    private String[] loadReset(String kind, String id) {
        if (db == null) return memoryResets.get(kind + "|" + id);
        try { return db.loadPasswordReset(kind, id); }
        catch (SQLException e) { reportDbError("Could not read the password reset request:\n" + e.getMessage(), e); return null; }
    }

    /** Stores the hashed reset code of an account (replacing any earlier one). */
    private boolean saveReset(String kind, String id, String hash, LocalDateTime expires) {
        if (db == null) {
            memoryResets.put(kind + "|" + id, new String[]{hash, expires.toString(), "0", LocalDateTime.now().toString()});
            return true;
        }
        try { db.savePasswordReset(kind, id, hash, expires); return true; }
        catch (SQLException e) { reportDbError("Could not save the password reset request:\n" + e.getMessage(), e); return false; }
    }

    /** Removes the pending reset code of an account. */
    private void deleteReset(String kind, String id) {
        if (db == null) { memoryResets.remove(kind + "|" + id); return; }
        persist("remove the password reset request", () -> db.deletePasswordReset(kind, id));
    }

    /** Counts one wrong reset code. */
    private void addResetAttempt(String kind, String id) {
        if (db == null) {
            String[] r = memoryResets.get(kind + "|" + id);
            if (r != null) r[2] = String.valueOf(Integer.parseInt(r[2]) + 1);
            return;
        }
        persist("count the password reset attempt", () -> db.addPasswordResetAttempt(kind, id));
    }

    /**
     * Step 1 of "Forgot password" -- quick, runs on the screen thread. Finds the account (a student's School ID or
     * e-mail, or a staff/admin e-mail), makes a 6-digit code, stores only its hash and prepares the e-mail.
     * The screen then calls {@link PendingReset#deliver} on a BACKGROUND thread and finally {@link #finishResetRequest}.
     * An unknown, deactivated or archived account looks the same to the person typing (no account guessing).
     */
    public PendingReset prepareResetRequest(String identifier) {
        if (!getMailSender().isConfigured()) {
            return PendingReset.failed("Password reset e-mails are not set up on this computer yet.\n\n"
                    + "Ask the system administrator to fill in the smtp.* settings in db.properties (see EMAIL_SETUP.md), "
                    + "or ask the Registrar Office / administrator to reset your password.");
        }
        reloadFromDatabase();
        Account acct = findAccountByLogin(identifier);
        if (acct == null || !acct.canLogin()) {
            addLog(identifier == null ? "" : identifier.trim(), "Password Reset Requested \u2014 No Matching Active Account", LogStatus.FAILED);
            return PendingReset.nothingToSend();
        }
        String kind = acct.getRole().name(), id = acct.getId();
        String[] existing = loadReset(kind, id);
        if (existing != null) {
            long age = java.time.Duration.between(LocalDateTime.parse(existing[3]), LocalDateTime.now()).getSeconds();
            if (age >= 0 && age < RESET_COOLDOWN_SECONDS) {
                return PendingReset.failed("A code was sent a moment ago. Please wait " + (RESET_COOLDOWN_SECONDS - age)
                        + " seconds before asking for another one.");
            }
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        if (!saveReset(kind, id, PasswordHasher.hash(code), LocalDateTime.now().plusMinutes(RESET_CODE_MINUTES))) {
            return PendingReset.failed("The reset request could not be saved. Please try again.");
        }
        String first = acct.getName().trim().split("\\s+")[0];
        String body = "Hello " + first + ",\n\n"
                + "We received a request to reset the password of your Student Module Management System account.\n\n"
                + "Your verification code is:  " + code + "\n\n"
                + "Enter this code in the app, together with your new password. The code works for "
                + RESET_CODE_MINUTES + " minutes and only once.\n\n"
                + "If you did not ask for this, you can ignore this e-mail \u2014 your password stays the same.\n";
        return new PendingReset(null, true, kind, id, acct.getEmail(), "Your password reset code", body);
    }

    /** Step 3 (screen thread, after the e-mail was handed to the mail server or failed). Pass the exception, or null on success.
     *  @return null when fine, otherwise the message to show */
    public String finishResetRequest(PendingReset p, Exception sendError) {
        if (p == null || !p.mustSend) return null;
        if (sendError != null) {
            deleteReset(p.kind, p.accountId);
            addLog(p.accountId, "Password Reset E-mail Failed", LogStatus.FAILED);
            String why = sendError.getMessage() == null ? sendError.toString() : sendError.getMessage();
            return "The e-mail could not be sent:\n" + why + "\n\nCheck the smtp.* settings in db.properties and the internet connection.";
        }
        addLog(p.accountId + " (" + p.kind.toLowerCase(Locale.ROOT) + ")", "Password Reset Code E-mailed", LogStatus.SUCCESS);
        return null;
    }

    /**
     * Step 4: the person typed the code and a new password.
     * @return null when the password was changed, otherwise the message to show
     */
    public String completePasswordReset(String identifier, String code, String newPassword) {
        reloadFromDatabase();
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return "Your new password must be at least " + MIN_PASSWORD_LENGTH + " characters long.";
        }
        String bad = "That code is not valid. Check the e-mail and try again, or request a new code.";
        Account acct = findAccountByLogin(identifier);
        if (acct == null || !acct.canLogin()) return bad;
        String kind = acct.getRole().name(), id = acct.getId();
        String[] r = loadReset(kind, id);
        if (r == null) return bad;
        if (LocalDateTime.parse(r[1]).isBefore(LocalDateTime.now())) {
            deleteReset(kind, id);
            return "That code has expired. Please request a new one.";
        }
        if (Integer.parseInt(r[2]) >= RESET_MAX_ATTEMPTS) {
            deleteReset(kind, id);
            return "Too many wrong codes. Please request a new one.";
        }
        String typed = code == null ? "" : code.trim();
        if (!PasswordHasher.matches(typed, r[0])) {
            addResetAttempt(kind, id);
            int left = RESET_MAX_ATTEMPTS - 1 - Integer.parseInt(r[2]);
            addLog(acct.getName() + " (" + roleLabel(acct) + ")", "Password Reset \u2014 Wrong Code", LogStatus.FAILED);
            return left > 0 ? "That code is not correct. You have " + left + " more tr" + (left == 1 ? "y." : "ies.")
                            : "Too many wrong codes. Please request a new one.";
        }
        changeAccountPassword(acct, newPassword);
        deleteReset(kind, id);
        failedLogins.remove(String.valueOf(identifier).trim().toLowerCase(Locale.ROOT));
        lockedUntil.remove(String.valueOf(identifier).trim().toLowerCase(Locale.ROOT));
        addLog(acct.getName() + " (" + roleLabel(acct) + ")", "Password Reset Completed", LogStatus.SUCCESS);
        return null;
    }
}
