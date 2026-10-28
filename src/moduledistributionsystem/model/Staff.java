package moduledistributionsystem.model;

/** Staff (module distributor / teacher) account. Adds the department and job title to the shared {@link Account} data. */
public class Staff extends Account {
    /** Department the staff member belongs to, e.g. "CITE". */
    private final String department;
    /** Job title shown in the sidebar, e.g. "Module Distributor". */
    private final String title;

    /** Creates an active staff account. */
    public Staff(String id, String name, String email, String password, String department, String title) {
        super(id, name, email, password);
        this.department = department;
        this.title = title;
    }

    /** Staff open the Staff portal. */
    @Override
    public Role getRole() { return Role.STAFF; }

    /** Wraps this staff member in the result that opens the Staff portal. */
    @Override
    public AuthResult toAuthResult() { return new AuthResult(this); }

    /** Staff sign in with their e-mail address. */
    @Override
    public String getLoginId() { return getEmail(); }

    /** The staff member's department. */
    public String getDepartment() { return department; }
    /** The staff member's job title. */
    public String getTitle() { return title; }
}
