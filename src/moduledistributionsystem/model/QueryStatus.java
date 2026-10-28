package moduledistributionsystem.model;

/**
 * Life cycle of a support ticket on the Inquiries Desk.
 *
 * - PENDING: waiting for staff.
 * - ANSWERED: staff replied.
 * - ESCALATED: staff passed it to the administrator.
 */
public enum QueryStatus {
    PENDING, ANSWERED, ESCALATED
}
