package moduledistributionsystem.model;

/**
 * State of a pickup appointment.
 *
 * - PENDING: the student booked it and staff have not answered yet.
 * - CONFIRMED: staff approved it (shown as "Approved" to staff, "Confirmed" to the student).
 * - COMPLETED: the student collected the module (shown as "Picked Up"). Stock was reduced by one.
 * - CANCELLED: staff declined it or the student cancelled it (shown as "Declined" / "Cancelled").
 *
 * The database stores the constant names exactly as written here, so do not rename them without
 * updating the saved data.
 */
public enum AppointmentStatus {
    PENDING, CONFIRMED, COMPLETED, CANCELLED
}
