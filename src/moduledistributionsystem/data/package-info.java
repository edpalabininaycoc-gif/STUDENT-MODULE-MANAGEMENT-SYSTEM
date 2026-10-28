/**
 * Business rules and storage.
 *
 * <ul>
 *   <li>{@code DemoDataStore}: every list and every rule (booking, slot limit, stock, login, reset password);</li>
 *   <li>{@code Database}: the only class that writes SQL (MySQL / MariaDB through JDBC), with {@code DbConfig};</li>
 *   <li>{@code Curriculum}: the official subject list; {@code PickupSchedule}: time slots and booking window;</li>
 *   <li>{@code PasswordHasher}: salted PBKDF2 hashing; {@code MailSender} / {@code SmtpMailer} / {@code MailConfig}:
 *       e-mail for password reset; {@code FifoQueue}: a first-in-first-out queue used for first come, first served.</li>
 * </ul>
 */
package moduledistributionsystem.data;
