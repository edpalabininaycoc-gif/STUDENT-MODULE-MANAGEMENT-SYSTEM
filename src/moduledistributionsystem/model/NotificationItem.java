package moduledistributionsystem.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One in-app notification for a student (appointment changes, out-of-stock alerts, query answers...).
 *
 * Created by DemoDataStore.addNotification and shown on the student's Notifications page. It starts
 * unread and can be marked read or dismissed.
 */
public class NotificationItem {
    /** Unique id (database primary key). */
    private UUID id;
    /** School ID of the student who receives it. */
    private String studentId;
    /**
     * Short title, e.g. "Appointment Confirmed". The Notifications page picks its colored tag from
     * words in the title.
     */
    private String title;
    /** Full message text. */
    private String message;
    /** When it was created. */
    private LocalDateTime timestamp;
    /** True after the student opened or marked it as read. */
    private boolean read;

    /** Creates a new, unread notification stamped with the current time. */
    public NotificationItem(String studentId, String title, String message) {
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.title = title;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.read = false;
    }

    /**
     * Rebuilds a notification exactly as it was saved in the database. Used only by
     * Database.loadNotifications.
     */
    public static NotificationItem restore(UUID id, String studentId, String title, String message,
                                           LocalDateTime timestamp, boolean read) {
        NotificationItem n = new NotificationItem(studentId, title, message);
        n.id = id;
        n.timestamp = timestamp;
        n.read = read;
        return n;
    }

    /** Unique id. */
    public UUID getId() { return id; }
    /** Recipient's School ID. */
    public String getStudentId() { return studentId; }
    /** Short title. */
    public String getTitle() { return title; }
    /** Full message text. */
    public String getMessage() { return message; }
    /** When it was created. */
    public LocalDateTime getTimestamp() { return timestamp; }
    /** True once read. */
    public boolean isRead() { return read; }
    /** Marks the notification as read. */
    public void markRead() { this.read = true; }

    /** "title - message", for logs. */
    @Override
    public String toString() {
        return title + " — " + message;
    }
}
