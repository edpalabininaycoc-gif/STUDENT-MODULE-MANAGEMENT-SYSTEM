package moduledistributionsystem.model;

import java.time.LocalDateTime;

/**
 * One row of the administrator's System Activity log (the audit trail): who did what, when, and whether
 * it worked.
 *
 * Rows are written by DemoDataStore.addLog (logins, failed logins, account changes, password
 * resets...). They are never edited or deleted by any screen, so the log stays trustworthy.
 */
public class SystemLogEntry {
    /** When it happened. */
    private LocalDateTime timestamp;
    /** Who did it (name and role, or the text typed on a failed login). */
    private String user;
    /** What happened, e.g. "Student Portal Login". */
    private String action;
    /**
     * Address shown in the log. A desktop app has no real network address, so a fixed demo value is
     * used.
     */
    private String ipAddress;
    /** SUCCESS or FAILED. */
    private LogStatus status;

    /** Creates a log row stamped with the current time. */
    public SystemLogEntry(String user, String action, String ipAddress, LogStatus status) {
        this.timestamp = LocalDateTime.now();
        this.user = user;
        this.action = action;
        this.ipAddress = ipAddress;
        this.status = status;
    }

    /** Rebuilds a log row exactly as it was saved in the database. Used only by Database.loadLogs. */
    public static SystemLogEntry restore(LocalDateTime timestamp, String user, String action, String ipAddress, LogStatus status) {
        SystemLogEntry e = new SystemLogEntry(user, action, ipAddress, status);
        e.timestamp = timestamp;
        return e;
    }

    /** When it happened. */
    public LocalDateTime getTimestamp() { return timestamp; }
    /** Who did it. */
    public String getUser() { return user; }
    /** What happened. */
    public String getAction() { return action; }
    /** Address column of the log. */
    public String getIpAddress() { return ipAddress; }
    /** SUCCESS or FAILED. */
    public LogStatus getStatus() { return status; }
}
