# Defense Guide - what was added and how to present it

This guide covers the final-demo rubric (2.5 minutes, 4 phases) for the desktop (Java) version of the system.
The rubric is written for PHP / HTML; below, every item has its desktop equivalent and where to find it.

## 1. Rubric items -> your system

| Rubric item | In your system | Where |
|---|---|---|
| MySQL on XAMPP, master schema script | Yes. Tables are created from `schema.sql` when the app starts | `resources/schema.sql` |
| 3NF, primary keys, foreign keys | Yes (new). Names / locations are read with JOINs; 5 foreign keys on `student_id` and `module_id` | `schema.sql`, `Database.normalizeSchema` |
| Junction table for a many-to-many | `appointments` (student <-> module, with its own data: time, status, officer) | diagram `erd_3nf.png` |
| CRUD | Create / read / update everywhere; modules can be deleted; accounts are archived. Appointments and tickets can NOT be archived or deleted (your ma'am's rule) | Staff / Admin portals |
| Server-side validation | Yes: empty fields, invalid e-mail, School ID format, 6+ character password, 10+ character query, duplicate module code / e-mail, past dates, full slots, no stock | `DemoDataStore`, forms |
| SQL-injection defense | Yes: every statement is a prepared statement with `?` placeholders | `Database` |
| XSS defense (`htmlspecialchars` in PHP) | Typed text is shown as plain text; where it goes into an HTML label it is escaped with `UIKit.escapeHtml` | `UIKit` |
| Friendly error banners, no raw crash text | Yes (new): database errors show one friendly sentence; the technical text goes to the console only | `ModuleDistributionSystem.main` |
| JOIN + GROUP BY summary reports | Yes (new): 4 reports on the Admin > Reports page | `ReportKind`, `ReportsPanel` |
| CSV export | Yes (new): Reports > Export CSV, and System Activity > Export Logs | `CsvUtil` |
| Print view (`@media print`) | Desktop equivalent (new): Reports > Print (no menus or buttons, title + table + page numbers; choose "Microsoft Print to PDF" to get a PDF) | `ReportsPanel.printReport` |
| Realistic data | Use real-looking names (see section 3) | - |

## 2. Install the update (do this first, with a backup)

1. In phpMyAdmin, export your database (`module_distribution`) as a backup. The first start DROPS two copied columns
   (`appointments.student_name`, `appointments.location`) and `student_queries.student_name`; this can not be undone
   without the backup. Nothing is lost: the same facts are read from `students` and `modules`.
2. Copy the files of the zip over your project (same paths, `src` folder over `src` folder), including
   `resources/schema.sql`. Clean and Build, then Run.
3. Read the NetBeans output on the first start. Good signs:
   `Schema upgrade: removed redundant column appointments.student_name` (and the other two) and
   `Schema upgrade: added foreign key fk_appointments_student` (and the others).
4. If you see `Could not add foreign key ...`: old rows point to a student that no longer exists. Find them with
   `SELECT a.id FROM appointments a LEFT JOIN students s ON s.id = a.student_id WHERE s.id IS NULL;`
   (same idea for `notifications`, `feedback`, `student_queries`), fix or delete those rows in phpMyAdmin, and restart.
   The program keeps working without the key; only the foreign key is missing.
5. Check in phpMyAdmin: `SHOW CREATE TABLE appointments;` shows `fk_appointments_student` and `fk_appointments_module`.

## 3. Prepare before the demo (realistic data, no "asdf" / "123")

* Student: **Maria Cristina Reyes**, School ID `02-2526-031482`, e-mail `mariacristina.reyes@example.edu`, 2nd Year.
* Staff: use your staff account (or create "Joshua Agsalan").
* Module: GEN 003 (Science, Technology and Society) - in Module Inventory set stock to 25 and "Max students per time slot" to 2.
* Open TWO copies of the app (one logged in as the student, one as staff) so you do not waste time logging in and out.
  Both use the same database; a page shows the other copy's changes when you click its menu item again.
* Put a few real bookings in first so the reports have numbers to show.

## 4. The 2.5-minute script

**Phase 1 - Context (30 s).** "Students used to line up at the module office. Staff checked every request by hand,
and students came back several times just to ask if a module was available. Our system lets students book a pickup time
online, limits how many can come at the same time, and keeps the stock count right."

**Phase 2 - Feature flow (60 s).**
1. Student window > Modules > click GEN 003 > pick tomorrow 10:00 AM > Confirm. ("Appointment requested.")
2. Staff window > Student Appointments > tick Maria's request > Approve Selected > Mark Picked Up.
3. Staff > Module Inventory: stock went from 25 to 24.
4. Student window > Notifications: the "Appointment Completed" message.
Say: "The booking, the approval and the stock change are saved in the database, in separate related tables."

**Phase 3 - Security proof (30 s).**
1. Student > Student Queries > click Submit with the message empty: the warning banner says
   "Please describe your query in at least 10 characters."
2. Type `<script>alert('hacked')</script>` as the message and Submit: it is saved and shown as plain text. Nothing runs.
3. Login screen: type `' OR '1'='1` as the ID and anything as the password: "Invalid ID/email or password."
   (Optional: five wrong passwords lock the login for a minute.)
Say: "Every database command uses prepared statements, so typed text can never become SQL, and typed text is shown as text."

**Phase 4 - Reporting value (30 s).**
1. Admin > Reports > "Appointment Details (Student + Module)".
2. Click **Show SQL**: the INNER JOIN of three tables.
3. Choose "Appointments by Module" (GROUP BY) and click **Export CSV**; open the file, or click **Print** and choose Print to PDF.
Say: "Administrators can see which modules are busiest and send the numbers to a spreadsheet."

## 4b. Questions the panel may ask

* **Is it 3NF?** Each fact is stored once: a student's name lives only in `students`, a module's location only in `modules`;
  appointments hold only the two foreign keys plus their own data. (`appointments.officer` and `system_logs.user_label` are
  deliberate audit snapshots.)
* **Where is the many-to-many?** `appointments` links students and modules.
* **How do you stop SQL injection?** Prepared statements (`?` placeholders) in every query in `Database`.
* **How do you stop XSS?** The desktop screens show typed text as plain text; in the few HTML labels it is escaped with `UIKit.escapeHtml`.
* **Why can't staff delete appointments?** The school's rule: records are kept for review; only deactivated accounts can be archived.
* **What if the database is down?** One friendly message; the technical text is in the console.

## 5. Test the new parts once on your computer

1. Run all four reports (Reports page). Each must show rows (or "No rows to show"), never an error box.
2. Show SQL, Export CSV (open it in Excel), Print (to PDF).
3. Book, approve and pick up an appointment; check that names and the location still show everywhere
   (Appointments, Notifications, Reports, Inquiries Desk).
4. Send a student query and check that its student name shows on the staff Inquiries Desk.
5. Stop MySQL in XAMPP and click around: you should get friendly messages, never a long error text.

## 6. Add to your P3 documentation

* Functional requirement: **FR-19** "The system shall let the administrator view summary reports built from related tables
  (appointments per module, appointment details, busiest time slots, students per year level), export them to CSV and print them."
* Admin scope row: add "view, export and print summary reports".
* Replace the database diagram with `erd_3nf.png`; add a screenshot of the Reports page and of Show SQL.
* Section 5.4 Security: add "typed text is escaped / shown as plain text; database errors are shown as friendly messages".
