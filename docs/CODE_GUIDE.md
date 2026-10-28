# Code Guide - Student Module Management System

This guide explains where everything is and how to change it. Every class also has comments (Javadoc) at the top
and on every field and method, so you can read the code in NetBeans by hovering over a name.

## 1. How the program is built (3 layers)

```
 screens (ui.*)  ->  rules (data.DemoDataStore)  ->  storage (data.Database -> MySQL)
   only show things      all the business rules        all the SQL, nothing else
```

* A screen never decides anything. It calls a method of `DemoDataStore` and shows the answer.
* `DemoDataStore` keeps the lists in memory, applies every rule, and calls `Database` to save.
* `Database` is the only class that writes SQL. Each statement uses `?` placeholders (no SQL injection).

Start the program with `ModuleDistributionSystem.main`. Order: look -> connect to database -> login window ->
Student / Staff / Admin main window.

## 2. Where is what (package map)

| Package | Classes | What they do |
|---|---|---|
| `model` | `Account` (abstract), `Student`, `Staff`, `Admin`, `Module`, `Appointment`, `NotificationItem`, `StudentQuery`, `Feedback`, `SystemLogEntry`, enums | Plain data classes. No screens, no SQL. |
| `data` | `DemoDataStore` | All rules: login, booking, slot limit, stock, approve / pick up, notifications, queries, reset password |
| | `Database`, `DbConfig` | All SQL, connection to MySQL / MariaDB, `db.properties` |
| | `Curriculum` | The official subject list (1st-4th year, 2 semesters) |
| | `PickupSchedule` | Time slots and how many days ahead a student may book |
| | `PasswordHasher` | Salted PBKDF2 password hashing |
| | `MailSender`, `SmtpMailer`, `MailConfig` | E-mail for "Forgot password" |
| | `FifoQueue` | Our own first-in-first-out queue |
| | `ReportKind`, `Report` | The admin reports: label, columns, the SQL text (JOIN / GROUP BY) and the formatted result |
| `ui.common` | `UIKit`, `CsvUtil`, `LoginFrame`, `LoginPanel`, `ForgotPasswordDialog` | Shared look, login screen, forgot password |
| `ui.student` | `StudentMainFrame`, `OverviewPanel`, `ModulesPanel`, `AppointmentsPanel`, `SlotPicker`, `AppointmentDialog`, `NotificationsPanel`, `StudentQueriesPanel`, `FeedbackPanel` (not in the menu) | Student portal |
| `ui.staff` | `StaffMainFrame`, `InventoryPanel`, `StaffAppointmentsPanel`, `QueriesPanel` | Staff portal. `InventoryPanel` also holds the shared page pieces (Page, RowPanel, SearchBox, Dropdown...). |
| `ui.admin` | `AdminMainFrame`, `ManageUsersPanel`, `AppointmentsOverridePanel`, `ReportsPanel`, `SystemActivityPanel` | Admin portal |

## 3. "I want to change..." cheat sheet

| I want to change | Edit this |
|---|---|
| The available pickup times | `PickupSchedule.SLOTS` |
| How many days ahead a student can book | `PickupSchedule.BOOKING_DAYS_AHEAD` |
| Default max students per time slot | `Module.DEFAULT_SLOT_LIMIT` (staff can change it per module in the Module editor) |
| Subjects (add / rename / units / year / semester) | `Curriculum` (one `add(...)` line per subject; the database is brought up to date on the next start) |
| Wrong passwords before a lock / lock time | `DemoDataStore.MAX_FAILED_LOGINS`, `DemoDataStore.LOCK_MILLIS` |
| Reset code lifetime, tries, wait between codes | `DemoDataStore.RESET_CODE_MINUTES`, `RESET_MAX_ATTEMPTS`, `RESET_COOLDOWN_SECONDS` |
| Shortest allowed password | `DemoDataStore.MIN_PASSWORD_LENGTH` |
| How slow / safe password hashing is | `PasswordHasher.ITERATIONS` |
| Database address, user, password | `db.properties` (`db.host`, `db.port`, `db.name`, `db.user`, `db.password`) |
| E-mail account used for reset codes | `db.properties` (`smtp.*`, see `EMAIL_SETUP.md`) |
| Demo accounts and passwords | `DemoDataStore.seedDemoData()` |
| Colors | the constants at the top of `UIKit` (and `InventoryPanel` for the page colors) |
| Sidebar menu items / pages | `MENU_LABELS`, `MENU_GLYPHS` and the card layout in `StudentMainFrame`, `StaffMainFrame`, `AdminMainFrame` |
| Table columns of a page | `LABELS`, `MINW`, `WEIGHT` at the top of that panel and its `buildRow(...)` |
| Question categories of Student Queries | `StudentQueriesPanel.CATEGORIES` |
| Wording of notifications | `DemoDataStore` (`availabilityChanged`, `approveAppointment`, `declineAppointment`, `cancelAppointment`, ...) |
| What is saved for a module | `Module` + `schema.sql` + `Database.saveModule / loadModules` + `InventoryPanel.ModuleEditDialog` |
| A new admin report | `ReportKind` (add a constant + its SQL), then a `case` in `Database.runReport` and in `DemoDataStore.buildReportInMemory`; it appears in the Reports drop-down by itself |
| A new kind of user | extend `Account`, add a `Role`, add its list + table (see the note at the top of `Account`) |

## 4. The four OOP pillars - where to point at in the code

| Pillar | Where it is | How to explain it |
|---|---|---|
| **Encapsulation** | Every model class (`Module`, `Appointment`, `Account`...) | Fields are `private`; the rest of the program uses getters / setters. A setter can protect a rule, e.g. `Module.setStocks` never stores a negative number and `setSlotLimit` never stores less than 1. |
| **Inheritance** | `Student`, `Staff`, `Admin` `extends` `Account`; every screen `extends` `JPanel` / `JFrame` / `JDialog` | The shared data (id, name, e-mail, password, active / archived) is written once in `Account`; each subclass adds only its own fields. |
| **Polymorphism** | `Account.getRole()`, `getLoginId()`, `toAuthResult()`, `matchesLogin()` are overridden by each subclass; `DemoDataStore.authenticate` loops over a `List<Account>`; `MailSender` -> `SmtpMailer`; overloaded constructors in `Module` and `AuthResult`; overridden `paintComponent` in the screens | One loop logs in a student, a staff member or an admin without any `if student... else if staff...`: each object answers in its own way. DemoDataStore only knows "a `MailSender`", not how the mail travels. |
| **Abstraction** | `abstract class Account` (with abstract methods); `interface MailSender`; `DemoDataStore` hides `Database`; `FifoQueue` hides its linked nodes | You use what an object DOES, not how it does it: a screen calls `store.bookAppointment(...)` and never sees the SQL, the lock or the slot counting. |

## 5. Data structures used - and where

| Structure | Where | Why |
|---|---|---|
| **FIFO queue** (our own class `FifoQueue`, a linked list with head and tail) | `DemoDataStore.markPickedUpInOrder`, `pickupQueueFor` | "First come, first served": approved students are served in the order they asked, so the last copy goes to the earliest request. `enqueue` / `dequeue` are O(1). |
| `ArrayList` | all the lists in `DemoDataStore` (modules, appointments, accounts...) | Ordered lists that are walked and filtered. |
| `HashMap` | `failedLogins`, `lockedUntil`, the reset codes in demo mode, id -> module maps in `Database` | Fast lookup by key. |
| `LinkedHashMap` | result of `markPickedUpInOrder`, table checkbox maps in the screens | A map that remembers insertion order. |
| `HashSet` / `LinkedHashSet` | `Student` course-load exceptions, dropdown choices | No duplicates; the Linked version keeps order. |
| Arrays | column widths (`MINW`, `WEIGHT`, `ALIGN`), `PickupSchedule.SLOTS`, `CATEGORIES` | Fixed-size lists known at compile time. |
| Sorting with `Comparator` | `StaffAppointmentsPanel` sorts, `pickupQueueFor` | Order by request time, pickup time or module code. |

Not used (be honest if asked): stack, priority queue, trees, graphs.

## 6. Database

* Tables are created automatically from `resources/schema.sql` when the program starts; new columns are added by
  `ensureColumn(...)` in `Database.connect`, so an older database upgrades itself.
* Tables: `students`, `staff`, `admins`, `modules`, `appointments`, `notifications`, `student_queries`, `feedback`,
  `student_module_overrides`, `password_resets`, `system_logs`, `app_settings`.
* Third normal form: a student's name and a module's location are NOT stored in `appointments` or `student_queries`;
  they are read with a JOIN. Foreign keys tie `appointments` (module, student), `notifications`, `feedback`,
  `student_queries` and `student_module_overrides` (student) to their parents. `appointments` is the associative
  (junction) table of the many-to-many relationship students <-> modules. A database made by an older version is
  upgraded on start by `Database.normalizeSchema` (each step is tried alone and only warns in the console if it fails).
* Reports (`ReportsPanel`): four real JOIN / GROUP BY queries (`ReportKind.sql()`), with Show SQL, Export CSV
  (`CsvUtil`) and Print. In demo mode (no database) the same numbers are computed in memory.
* Booking a slot and handing out the last copy run inside a transaction (see `Database.saveAppointmentIfSlotFree`
  and `Database.completePickup`), so two computers can not take the same last place or copy.

## 7. Rules that are on purpose

* Appointments and tickets can NOT be archived or deleted by staff or the administrator.
* Only a deactivated account can be archived (admin); archived accounts can not sign in.
* A booking does not reserve a copy: stock goes down only when a pickup is marked Picked Up.
