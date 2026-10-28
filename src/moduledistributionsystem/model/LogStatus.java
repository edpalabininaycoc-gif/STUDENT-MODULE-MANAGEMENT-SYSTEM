package moduledistributionsystem.model;

/**
 * Outcome of a logged system event: SUCCESS or FAILED (for example a wrong password). Stored by name in
 * the system log table.
 */
public enum LogStatus {
    SUCCESS, FAILED
}
