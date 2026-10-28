/**
 * The data classes ("models"). They only hold values and tiny rules; they know nothing about screens or SQL.
 *
 * <ul>
 *   <li>{@code Account} (abstract) -> {@code Student}, {@code Staff}, {@code Admin}: the people who sign in;</li>
 *   <li>{@code Module}, {@code Appointment}, {@code NotificationItem}, {@code StudentQuery}, {@code Feedback},
 *       {@code SystemLogEntry}: the things the system keeps track of;</li>
 *   <li>enums {@code Role}, {@code YearLevel}, {@code ModuleStatus}, {@code AppointmentStatus},
 *       {@code QueryStatus}, {@code QueryPriority}, {@code LogStatus}: the fixed sets of choices;</li>
 *   <li>{@code AuthResult}: what a successful login returns.</li>
 * </ul>
 */
package moduledistributionsystem.model;
