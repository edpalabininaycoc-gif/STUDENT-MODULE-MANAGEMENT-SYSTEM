package moduledistributionsystem.model;

/**
 * Abstract base class of every account that can sign in: {@link Student}, {@link Staff} and {@link Admin}.
 *
 * <p><b>Why it exists (OOP):</b> the three account types share the same data (id, name, e-mail, password,
 * active / archived flags) and the same rules (a deactivated or archived account can not sign in). That
 * shared part lives here once (<i>inheritance</i>), the fields are private and only reachable through
 * getters / setters (<i>encapsulation</i>), and each subclass only adds what is special about it:
 * <ul>
 *   <li>{@link #getRole()} and {@link #getLoginId()} are <i>abstract</i>: every subclass must answer them
 *       in its own way, and code that holds an {@code Account} can simply call them
 *       (<i>polymorphism</i>) instead of checking {@code instanceof Student / Staff / Admin}.</li>
 * </ul>
 *
 * <p><b>How to customize:</b> to add a new kind of user (for example a "Registrar"), create a class that
 * {@code extends Account}, implement the two abstract methods, add a value to {@link Role}, and add its
 * list + database table next to the student / staff / admin ones in {@code DemoDataStore} and {@code Database}.
 */
public abstract class Account {

    /** Unique id: a student's School ID (e.g. 02-2526-015176) or a staff / admin id (e.g. ADM-001). Never changes. */
    private final String id;

    /** Full name shown in the screens. */
    private final String name;

    /** E-mail address. Must be unique across ALL accounts; the password-reset code is sent here. */
    private final String email;

    /** The stored password. After {@code DemoDataStore} saves it, this is a salted PBKDF2 hash, never plain text. */
    private String password;

    /** False = deactivated by an administrator. A deactivated account can not sign in. */
    private boolean active;

    /** True = tucked away by an administrator (only a deactivated account can be archived). It can not sign in. */
    private boolean archived;

    /**
     * Creates a new, active, not-archived account.
     *
     * @param id       unique id (School ID for students)
     * @param name     full name
     * @param email    unique e-mail address
     * @param password the password (plain text here; the data layer hashes it before saving)
     */
    protected Account(String id, String name, String email, String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.active = true;
    }

    /** Which portal this account opens after login. Each subclass returns its own {@link Role}. */
    public abstract Role getRole();

    /**
     * Builds the {@link AuthResult} that tells the login screen which portal to open. Abstract because each
     * subclass wraps ITSELF with the matching AuthResult constructor (Student, Staff or Admin).
     */
    public abstract AuthResult toAuthResult();

    /**
     * True when the text a person typed on the login screen identifies this account. By default that is the
     * e-mail address; {@link Student} overrides it so a School ID works too (<i>method overriding</i>).
     */
    public boolean matchesLogin(String typed) {
        return email.equalsIgnoreCase(typed);
    }

    /** What this person types in the login box: a student's School ID, or a staff / admin's e-mail. */
    public abstract String getLoginId();

    /** Short readable role name used in tables and logs: "Student", "Staff" or "Admin". */
    public String getRoleName() {
        switch (getRole()) {
            case STUDENT: return "Student";
            case STAFF: return "Staff";
            default: return "Admin";
        }
    }

    /** True when this account is allowed to sign in (not deactivated and not archived). */
    public boolean canLogin() { return active && !archived; }

    /** The unique id. */
    public String getId() { return id; }
    /** The full name. */
    public String getName() { return name; }
    /** The e-mail address. */
    public String getEmail() { return email; }
    /** The stored password (a hash once saved). */
    public String getPassword() { return password; }

    /** Replaces the stored password (callers pass the already-hashed value). */
    public void setPassword(String password) { this.password = password; }

    /** False when an administrator deactivated the account. */
    public boolean isActive() { return active; }
    /** Activates or deactivates the account. */
    public void setActive(boolean active) { this.active = active; }
    /** True when the account is archived. */
    public boolean isArchived() { return archived; }
    /** Archives or restores the account. */
    public void setArchived(boolean archived) { this.archived = archived; }
}
