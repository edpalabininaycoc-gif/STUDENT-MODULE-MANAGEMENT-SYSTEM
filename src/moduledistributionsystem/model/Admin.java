package moduledistributionsystem.model;

/** Administrator account. Adds the job title to the shared {@link Account} data. */
public class Admin extends Account {
    /** Job title shown in the sidebar, e.g. "Administrator". */
    private final String title;

    /** Creates an active administrator account. */
    public Admin(String id, String name, String email, String password, String title) {
        super(id, name, email, password);
        this.title = title;
    }

    /** Administrators open the Admin portal. */
    @Override
    public Role getRole() { return Role.ADMIN; }

    /** Wraps this administrator in the result that opens the Admin portal. */
    @Override
    public AuthResult toAuthResult() { return new AuthResult(this); }

    /** Administrators sign in with their e-mail address. */
    @Override
    public String getLoginId() { return getEmail(); }

    /** The administrator's job title. */
    public String getTitle() { return title; }
}
