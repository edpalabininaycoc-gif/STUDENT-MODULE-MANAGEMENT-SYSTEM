package moduledistributionsystem.model;

/**
 * Distribution status that staff set for a module.
 *
 * - PENDING: coming soon, students can not book yet.
 * - READY: open for pickup (students can book while stock is above 0).
 * - RELEASED: closed / already handed out, students can not book.
 *
 * The database stores the constant names exactly as written here.
 */
public enum ModuleStatus {
    PENDING, READY, RELEASED
}
