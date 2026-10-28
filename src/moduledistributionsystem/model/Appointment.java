package moduledistributionsystem.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * One pickup appointment: which student comes for which module, when, and in what state it is.
 *
 * Life cycle (see AppointmentStatus): PENDING (student booked) -> CONFIRMED (staff approved) ->
 * COMPLETED (picked up), or CANCELLED (declined / cancelled). A student may reschedule a pending /
 * approved one, which sets it back to PENDING. The rules that move an appointment between states live
 * in DemoDataStore, not here; this class only holds the data.
 */
public class Appointment {
    /** Unique id (database primary key). */
    private UUID id;
    /** The module the student wants to collect. */
    private Module module;
    /** The booked pickup date and time slot. */
    private LocalDateTime dateTime;
    /** Where to collect it (copied from the module when booked). */
    private String location;
    /** Current state in the life cycle described above. */
    private AppointmentStatus status;
    /**
     * Staff member in charge. Starts as "Registrar Office" and becomes the approving staff member's
     * name when approved.
     */
    private String officer;
    /** Name of the student who booked (copied so old appointments still show it). */
    private String studentName;
    /** School ID of the student who booked. */
    private String studentId;
    /** When the student made the request. This decides who is first in line (first come, first served). */
    private LocalDateTime requestedAt;
    /**
     * Kept only so old database rows still load. No screen archives appointments any more (archive and
     * delete are not allowed), so this is normally false.
     */
    private boolean archived;

    /**
     * Creates a new PENDING appointment requested right now, with a new random id. The student fields
     * are set afterwards with setStudentName / setStudentId.
     */
    public Appointment(Module module, LocalDateTime dateTime, String location, String officer) {
        this.id = UUID.randomUUID();
        this.module = module;
        this.dateTime = dateTime;
        this.location = location;
        this.status = AppointmentStatus.PENDING;
        this.officer = officer;
        this.requestedAt = LocalDateTime.now();
    }

    /**
     * Rebuilds an appointment exactly as it was saved in the database. Used only by
     * Database.loadAppointments.
     */
    public static Appointment restore(UUID id, Module module, LocalDateTime dateTime, String location, AppointmentStatus status,
                                      String officer, String studentName, String studentId, LocalDateTime requestedAt, boolean archived) {
        Appointment a = new Appointment(module, dateTime, location, officer);
        a.archived = archived;
        a.id = id;
        a.status = status;
        a.studentName = studentName;
        a.studentId = studentId;
        a.requestedAt = requestedAt;
        return a;
    }

    /** Unique id. */
    public UUID getId() { return id; }
    /** The module to collect. */
    public Module getModule() { return module; }
    /** Booked pickup date and time. */
    public LocalDateTime getDateTime() { return dateTime; }
    /** Moves the appointment to another time (used when rescheduling). */
    public void setDateTime(LocalDateTime dt) { this.dateTime = dt; }
    /** Pickup location. */
    public String getLocation() { return location; }
    /** Current status. */
    public AppointmentStatus getStatus() { return status; }
    /**
     * Changes the status. Screens should go through DemoDataStore so stock and notifications stay
     * correct.
     */
    public void setStatus(AppointmentStatus status) { this.status = status; }
    /** Staff member in charge. */
    public String getOfficer() { return officer; }
    /** Sets the staff member in charge. */
    public void setOfficer(String officer) { this.officer = officer; }
    /** Student name, or "Unknown Student" when it was never set. */
    public String getStudentName() { return studentName == null ? "Unknown Student" : studentName; }
    /** Sets the student name. */
    public void setStudentName(String studentName) { this.studentName = studentName; }
    /** Student School ID, or "-" when it was never set. */
    public String getStudentId() { return studentId == null ? "-" : studentId; }
    /** Sets the student School ID. */
    public void setStudentId(String studentId) { this.studentId = studentId; }
    /** When the request was made (decides first come, first served). */
    public LocalDateTime getRequestedAt() { return requestedAt; }
    /** True for an appointment archived in an older version. */
    public boolean isArchived() { return archived; }
    /** Sets the archived flag (not used by any screen any more). */
    public void setArchived(boolean archived) { this.archived = archived; }

    /** Readable one-line description for logs. */
    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");
        return module.getCode() + " — " + module.getTitle() + " | " + fmt.format(dateTime) + " | " + status;
    }
}
