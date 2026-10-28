package moduledistributionsystem.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Student account. Adds the year level and the manual course-load exceptions to the shared Account
 * data.
 *
 * A student's course load (the modules shown on their Modules page) is decided automatically: modules
 * of their own year level, for the current term. Exceptions (a repeat student, a cross-year elective)
 * can be added or hidden by the administrator in Manage Users > Manage > Enrollment.
 */
public class Student extends Account {
    /**
     * This student's year level. Set by the administrator when the account is created (and changed at
     * promotion). It drives which modules the student sees - see isEnrolledIn(Module).
     */
    private YearLevel yearLevel;

    /**
     * Manual "extra" exceptions: a module code in this set shows for the student even if it belongs to
     * another year level (e.g. a repeat student retaking an earlier subject). Normally empty.
     */
    private final Set<String> extraEnrolledModuleCodes = new LinkedHashSet<>();

    /**
     * Manual "hide" exceptions: a module code in this set is hidden from the student even if it matches
     * their year level (e.g. already passed). Checked first, so it always wins.
     */
    private final Set<String> droppedModuleCodes = new LinkedHashSet<>();

    /** Creates an active student account. The year level is set afterwards with setYearLevel. */
    public Student(String id, String name, String email, String password) {
        super(id, name, email, password);
    }

    /** Students open the Student portal. */
    @Override
    public Role getRole() { return Role.STUDENT; }

    /** Wraps this student in the result that opens the Student portal. */
    @Override
    public AuthResult toAuthResult() { return new AuthResult(this); }

    /** Students sign in with their School ID. */
    @Override
    public String getLoginId() { return getId(); }

    /** A student may type the School ID or the e-mail address. */
    @Override
    public boolean matchesLogin(String typed) {
        return getId().equalsIgnoreCase(typed) || super.matchesLogin(typed);
    }

    /** The student's year level (may be null for old accounts). */
    public YearLevel getYearLevel() { return yearLevel; }
    /** Sets the student's year level. */
    public void setYearLevel(YearLevel yearLevel) { this.yearLevel = yearLevel; }

    /**
     * Adds a manual exception: this module code shows up for the student regardless of the year-level
     * match (no effect if already added).
     */
    public void enrollInModule(String moduleCode) {
        if (moduleCode != null) extraEnrolledModuleCodes.add(moduleCode);
    }

    /**
     * Removes a manual "extra" exception. It does not change the normal year-level match; use
     * dropModule() to hide a module that matches the student's year level.
     */
    public void unenrollFromModule(String moduleCode) {
        extraEnrolledModuleCodes.remove(moduleCode);
    }

    /**
     * Adds a manual "hide" exception: this module code is hidden from the student even if it matches
     * their year level (no effect if already added).
     */
    public void dropModule(String moduleCode) {
        if (moduleCode != null) droppedModuleCodes.add(moduleCode);
    }

    /**
     * Removes a manual "hide" exception, so the normal year-level match (or an "extra" exception)
     * decides again.
     */
    public void undropModule(String moduleCode) {
        droppedModuleCodes.remove(moduleCode);
    }

    /** The manual "extra" module codes (the live set, not a copy). */
    public Set<String> getExtraEnrolledModuleCodes() { return extraEnrolledModuleCodes; }
    /** The manual "hidden" module codes (the live set, not a copy). */
    public Set<String> getDroppedModuleCodes() { return droppedModuleCodes; }

    /**
     * Whether this student's course load includes the module: true when the module's year level equals
     * the student's, or its code was added as an "extra" exception - unless it was also dropped, which
     * always wins. A student with no year level on record sees everything (the check "fails open")
     * instead of silently hiding the whole catalog.
     */
    public boolean isEnrolledIn(Module m) {
        if (m == null) return false;
        if (droppedModuleCodes.contains(m.getCode())) return false;
        if (yearLevel == null) return true;
        if (yearLevel == m.getYearLevel()) return true;
        return extraEnrolledModuleCodes.contains(m.getCode());
    }
}
