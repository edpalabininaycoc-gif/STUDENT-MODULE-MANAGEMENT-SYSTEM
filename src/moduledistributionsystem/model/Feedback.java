package moduledistributionsystem.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One feedback entry a student sent about the module distribution service (a 1-5 rating plus an
 * optional comment).
 */
public class Feedback {
    /** Unique id (database primary key). */
    private UUID id;
    /** School ID of the student who sent it. */
    private String studentId;
    /** What the feedback is about. */
    private String category;
    /** Rating from 1 (worst) to 5 (best). */
    private int rating; // 1-5
    /** Optional comment text. */
    private String comment;
    /** When it was sent. */
    private LocalDateTime submittedAt;

    /** Creates a new feedback entry stamped with the current time and a new random id. */
    public Feedback(String studentId, String category, int rating, String comment) {
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.category = category;
        this.rating = rating;
        this.comment = comment;
        this.submittedAt = LocalDateTime.now();
    }

    /**
     * Rebuilds a feedback entry exactly as it was saved in the database. Used only by
     * Database.loadFeedback.
     */
    public static Feedback restore(UUID id, String studentId, String category, int rating, String comment,
                                   LocalDateTime submittedAt) {
        Feedback f = new Feedback(studentId, category, rating, comment);
        f.id = id;
        f.submittedAt = submittedAt;
        return f;
    }

    /** Unique id. */
    public UUID getId() { return id; }
    /** School ID of the sender. */
    public String getStudentId() { return studentId; }
    /** What the feedback is about. */
    public String getCategory() { return category; }
    /** Rating from 1 to 5. */
    public int getRating() { return rating; }
    /** Comment text. */
    public String getComment() { return comment; }
    /** When it was sent. */
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
