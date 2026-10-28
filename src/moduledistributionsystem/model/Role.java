package moduledistributionsystem.model;

/**
 * Which portal a signed-in account is routed to: STUDENT, STAFF or ADMIN.
 *
 * Used by AuthResult, by Account.getRole() and as the "kind" of an account in the password-reset table.
 */
public enum Role {
    STUDENT, STAFF, ADMIN
}
