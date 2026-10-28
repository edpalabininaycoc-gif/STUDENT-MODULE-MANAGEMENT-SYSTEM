package moduledistributionsystem.model;

/**
 * Result of a successful login: the role that logged in and exactly one profile (student, staff or
 * admin). LoginPanel reads it to open the right main window.
 *
 * The three constructors are an example of method overloading: the one you call decides the role.
 */
public class AuthResult {
    /** Which portal to open. */
    private final Role role;
    /** The student profile (null unless role is STUDENT). */
    private final Student student;
    /** The staff profile (null unless role is STAFF). */
    private final Staff staff;
    /** The admin profile (null unless role is ADMIN). */
    private final Admin admin;

    /** A student logged in. */
    public AuthResult(Student student) {
        this.role = Role.STUDENT;
        this.student = student;
        this.staff = null;
        this.admin = null;
    }

    /** A staff member logged in. */
    public AuthResult(Staff staff) {
        this.role = Role.STAFF;
        this.student = null;
        this.staff = staff;
        this.admin = null;
    }

    /** An administrator logged in. */
    public AuthResult(Admin admin) {
        this.role = Role.ADMIN;
        this.student = null;
        this.staff = null;
        this.admin = admin;
    }

    /** Which portal to open. */
    public Role getRole() { return role; }
    /** The student profile, or null. */
    public Student getStudent() { return student; }
    /** The staff profile, or null. */
    public Staff getStaff() { return staff; }
    /** The admin profile, or null. */
    public Admin getAdmin() { return admin; }
}
