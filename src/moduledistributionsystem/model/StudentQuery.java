package moduledistributionsystem.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A student's question or concern for staff, shown as a support ticket on the Inquiries Desk.
 *
 * Life cycle (see QueryStatus): PENDING (waiting for staff) -> ANSWERED (staff replied) or ESCALATED
 * (staff passed it to the administrator, who replies). Created from the student's Student Queries page.
 */
public class StudentQuery {
    /** Unique id (database primary key). */
    private UUID id;
    /** Human-friendly ticket number shown as #T-nnn. Unique. */
    private int ticketNumber;
    /** School ID of the student who sent it. */
    private String studentId;
    /** Name of the student who sent it. */
    private String studentName;
    /** Code of the related module (stored without a space, e.g. "ITE031"). */
    private String subjectCode; // related module code
    /** Kind of question (e.g. "Module availability"). Chosen from a list on the student's form. */
    private String category;
    /** How urgent it is (LOW, MEDIUM, HIGH). */
    private QueryPriority priority;
    /** Where the ticket is in its life cycle. */
    private QueryStatus status;
    /** The student's question text. */
    private String message;
    /** When the ticket was sent. */
    private LocalDateTime submittedAt;
    /** When the ticket was first answered; null until then. Used by the "Answered Today" statistic. */
    private LocalDateTime answeredAt; // null until status first becomes ANSWERED
    /** The staff / administrator reply ("" until answered). */
    private String reply;
    /**
     * Kept only so old database rows still load. No screen archives tickets any more, so this is
     * normally false.
     */
    private boolean archived;

    /** Creates a new PENDING ticket submitted right now, with a new random id and an empty reply. */
    public StudentQuery(int ticketNumber, String studentId, String studentName, String subjectCode, String category,
                         QueryPriority priority, String message) {
        this.id = UUID.randomUUID();
        this.ticketNumber = ticketNumber;
        this.studentId = studentId;
        this.studentName = studentName;
        this.subjectCode = subjectCode;
        this.category = category;
        this.priority = priority;
        this.status = QueryStatus.PENDING;
        this.message = message;
        this.submittedAt = LocalDateTime.now();
        this.answeredAt = null;
        this.reply = "";
    }

    /** Rebuilds a ticket exactly as it was saved in the database. Used only by Database.loadQueries. */
    public static StudentQuery restore(UUID id, int ticketNumber, String studentId, String studentName, String subjectCode,
                                       String category, QueryPriority priority, QueryStatus status, String message,
                                       LocalDateTime submittedAt, LocalDateTime answeredAt, String reply, boolean archived) {
        StudentQuery q = new StudentQuery(ticketNumber, studentId, studentName, subjectCode, category, priority, message);
        q.id = id;
        q.status = status;
        q.submittedAt = submittedAt;
        q.answeredAt = answeredAt;
        q.reply = reply == null ? "" : reply;
        q.archived = archived;
        return q;
    }

    /** Unique id. */
    public UUID getId() { return id; }
    /** Ticket number shown as #T-nnn. */
    public int getTicketNumber() { return ticketNumber; }
    /** School ID of the sender. */
    public String getStudentId() { return studentId; }
    /** Name of the sender. */
    public String getStudentName() { return studentName; }
    /** Related module code. */
    public String getSubjectCode() { return subjectCode; }
    /** Kind of question. */
    public String getCategory() { return category; }
    /** Urgency. */
    public QueryPriority getPriority() { return priority; }
    /** Current status. */
    public QueryStatus getStatus() { return status; }
    /**
     * Changes the status. Setting ANSWERED for the first time also stamps answeredAt, so statistics
     * like "Answered Today" measure the reply date and not the submission date.
     */
    public void setStatus(QueryStatus status) {
        if (status == QueryStatus.ANSWERED && this.answeredAt == null) this.answeredAt = LocalDateTime.now();
        this.status = status;
    }
    /** The student's question text. */
    public String getMessage() { return message; }
    /** When it was sent. */
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    /** When it was first answered (null if never). */
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    /** The reply text. */
    public String getReply() { return reply; }
    /** Sets the reply text. */
    public void setReply(String reply) { this.reply = reply; }
    /** True for a ticket archived in an older version. */
    public boolean isArchived() { return archived; }
    /** Sets the archived flag (not used by any screen any more). */
    public void setArchived(boolean archived) { this.archived = archived; }
}
