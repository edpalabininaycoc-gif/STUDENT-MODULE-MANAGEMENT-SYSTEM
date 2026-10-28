-- Module Distribution System: database schema (MySQL / MariaDB, e.g. XAMPP)
-- The app runs this file automatically on start-up (every statement is CREATE TABLE IF NOT EXISTS,
-- so it is safe to run again). You can also import it by hand in phpMyAdmin: create a database,
-- select it, then use Import and pick this file.
-- Passwords are stored as salted PBKDF2 hashes, never as plain text.
--
-- Design notes (third normal form, 3NF):
--  * Every table has a primary key. Relationships use foreign keys: appointments -> modules and students,
--    notifications / feedback / student_queries / student_module_overrides -> students.
--  * "appointments" is the associative (junction) table of the many-to-many relationship between students and
--    modules: one student can book many modules and one module is booked by many students.
--  * A student's name and a module's location are NOT copied into appointments / student_queries: they are read
--    with a JOIN, so each fact is stored exactly once.
--  * system_logs.user_label is a deliberate snapshot (an audit trail must keep what happened, even for a login
--    attempt by someone who is not an account), and appointments.officer records who handled that appointment.

CREATE TABLE IF NOT EXISTS students (
    seq BIGINT NOT NULL AUTO_INCREMENT,
    id VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_students_seq (seq),
    UNIQUE KEY uq_students_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS staff (
    seq BIGINT NOT NULL AUTO_INCREMENT,
    id VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    department VARCHAR(80) NOT NULL,
    title VARCHAR(80) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_staff_seq (seq),
    UNIQUE KEY uq_staff_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admins (
    seq BIGINT NOT NULL AUTO_INCREMENT,
    id VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    title VARCHAR(80) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_admins_seq (seq),
    UNIQUE KEY uq_admins_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- deleted = 1 means "removed from the inventory" but the row is kept so old appointments still show it
CREATE TABLE IF NOT EXISTS modules (
    seq BIGINT NOT NULL AUTO_INCREMENT,
    id CHAR(36) NOT NULL,
    code VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    credits INT NOT NULL DEFAULT 3,
    location VARCHAR(100) NOT NULL,
    collect_by DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    semester VARCHAR(40) NOT NULL,
    department VARCHAR(40) NOT NULL,
    stocks INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_modules_seq (seq),
    KEY idx_modules_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointments (
    id CHAR(36) NOT NULL,
    module_id CHAR(36) NOT NULL,
    appt_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    officer VARCHAR(120) NOT NULL,
    student_id VARCHAR(40) NOT NULL,
    requested_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_appointments_student (student_id),
    KEY idx_appointments_module (module_id),
    CONSTRAINT fk_appointments_module FOREIGN KEY (module_id) REFERENCES modules (id),
    CONSTRAINT fk_appointments_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id CHAR(36) NOT NULL,
    student_id VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_notifications_student (student_id),
    CONSTRAINT fk_notifications_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feedback (
    id CHAR(36) NOT NULL,
    student_id VARCHAR(40) NOT NULL,
    category VARCHAR(80) NOT NULL,
    rating INT NOT NULL,
    comment_text TEXT NOT NULL,
    submitted_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_feedback_student (student_id),
    CONSTRAINT fk_feedback_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_queries (
    id CHAR(36) NOT NULL,
    ticket_number INT NOT NULL,
    student_id VARCHAR(40) NOT NULL,
    subject_code VARCHAR(30) NOT NULL,
    category VARCHAR(80) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    submitted_at DATETIME NOT NULL,
    answered_at DATETIME NULL,
    reply TEXT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_queries_ticket (ticket_number),
    KEY idx_queries_student (student_id),
    KEY idx_queries_status (status),
    CONSTRAINT fk_queries_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME NOT NULL,
    user_label VARCHAR(200) NOT NULL,
    action_text VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_logs_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- year_level was added after the tables above already existed in some installs (a plain
-- CREATE TABLE IF NOT EXISTS above is a no-op once the table exists, so it can't add a new
-- column to it). Adding it here isn't reliable across MySQL/MariaDB versions ("ADD COLUMN IF
-- NOT EXISTS" support is inconsistent), so it's done in Java instead: Database.connect() calls
-- ensureColumn("students", "year_level", ...) and the same for "modules" right after this file
-- runs, checking JDBC metadata rather than relying on that SQL syntax.

-- Manual enrollment exceptions on top of the normal year-level match (a repeat student retaking
-- an earlier subject, a cross-year elective, or hiding a module that would otherwise auto-match).
-- See Student.isEnrolledIn(Module) for how these two kinds are used.
CREATE TABLE IF NOT EXISTS student_module_overrides (
    student_id VARCHAR(40) NOT NULL,
    module_code VARCHAR(30) NOT NULL,
    kind VARCHAR(10) NOT NULL, -- 'EXTRA' or 'DROPPED'
    PRIMARY KEY (student_id, module_code, kind),
    CONSTRAINT fk_overrides_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Small system-wide key/value settings table. Currently holds one row: current_semester (e.g.
-- "Semester 1"), which decides which modules are "being distributed right now" on top of the
-- year-level match -- see ModulesPanel's filtering.
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(50) NOT NULL,
    setting_value VARCHAR(200) NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One-time codes for "Forgot password". Only a salted hash of the 6-digit code is stored, never the code itself.
-- account_kind is STUDENT, STAFF or ADMIN. A new request replaces the previous one for the same account.
CREATE TABLE IF NOT EXISTS password_resets (
    account_kind VARCHAR(10) NOT NULL,
    account_id VARCHAR(40) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (account_kind, account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
