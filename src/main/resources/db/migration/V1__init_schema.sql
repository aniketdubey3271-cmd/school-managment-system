-- Core schema for the Student Management System (SQLite).

CREATE TABLE users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,          -- BCrypt hash
    email       VARCHAR(100) NOT NULL UNIQUE,
    role        VARCHAR(20)  NOT NULL,          -- ADMIN, TEACHER, STUDENT
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE teachers (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id                 INTEGER       NOT NULL UNIQUE,
    name                    VARCHAR(100) NOT NULL,
    subject_specialization  VARCHAR(100),
    contact                 VARCHAR(20),
    CONSTRAINT fk_teachers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- e.g. class_name "10", section "A"
CREATE TABLE classes (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    class_name        VARCHAR(50) NOT NULL,
    section           VARCHAR(10) NOT NULL,
    class_teacher_id  INTEGER,
    CONSTRAINT uq_classes_name_section UNIQUE (class_name, section),
    CONSTRAINT fk_classes_class_teacher FOREIGN KEY (class_teacher_id) REFERENCES teachers (id)
);

CREATE TABLE students (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER       NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    roll_no         VARCHAR(20)  NOT NULL,
    class_id        INTEGER,
    dob             DATE,
    address         VARCHAR(255),
    parent_contact  VARCHAR(20),
    CONSTRAINT uq_students_class_roll UNIQUE (class_id, roll_no),
    CONSTRAINT fk_students_user  FOREIGN KEY (user_id)  REFERENCES users (id),
    CONSTRAINT fk_students_class FOREIGN KEY (class_id) REFERENCES classes (id)
);

-- Subject catalogue (Maths, Science, ...); who teaches it where lives in class_subject_teacher.
CREATE TABLE subjects (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    subject_name  VARCHAR(100) NOT NULL UNIQUE
);

-- Which teacher teaches which subject in which class. Drives a teacher's "My Classes".
CREATE TABLE class_subject_teacher (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    class_id    INTEGER NOT NULL,
    subject_id  INTEGER NOT NULL,
    teacher_id  INTEGER NOT NULL,
    CONSTRAINT uq_cst_class_subject UNIQUE (class_id, subject_id),
    CONSTRAINT fk_cst_class   FOREIGN KEY (class_id)   REFERENCES classes (id),
    CONSTRAINT fk_cst_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_cst_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id)
);

CREATE TABLE attendance (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id  INTEGER      NOT NULL,
    date        DATE        NOT NULL,
    status      VARCHAR(10) NOT NULL,           -- PRESENT, ABSENT
    marked_by   INTEGER,
    CONSTRAINT uq_attendance_student_date UNIQUE (student_id, date),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_attendance_teacher FOREIGN KEY (marked_by)  REFERENCES teachers (id)
);

CREATE TABLE grades (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id  INTEGER       NOT NULL,
    subject_id  INTEGER       NOT NULL,
    exam_type   VARCHAR(50)  NOT NULL,          -- e.g. UNIT_TEST_1, MIDTERM, FINAL
    marks       DECIMAL(5,2) NOT NULL,
    max_marks   DECIMAL(5,2) NOT NULL,
    entered_by  INTEGER,
    CONSTRAINT uq_grades_student_subject_exam UNIQUE (student_id, subject_id, exam_type),
    CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_grades_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_grades_teacher FOREIGN KEY (entered_by) REFERENCES teachers (id)
);

CREATE TABLE assignments (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    teacher_id   INTEGER       NOT NULL,
    class_id     INTEGER       NOT NULL,
    subject_id   INTEGER,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    due_date     DATETIME     NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignments_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id),
    CONSTRAINT fk_assignments_class   FOREIGN KEY (class_id)   REFERENCES classes (id),
    CONSTRAINT fk_assignments_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
);

CREATE TABLE submissions (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    assignment_id  INTEGER       NOT NULL,
    student_id     INTEGER       NOT NULL,
    file_url       VARCHAR(500) NOT NULL,
    submitted_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_submissions_assignment_student UNIQUE (assignment_id, student_id),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_submissions_student    FOREIGN KEY (student_id)    REFERENCES students (id)
);

-- target_class_id NULL = school-wide announcement
CREATE TABLE announcements (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    posted_by        INTEGER       NOT NULL,
    title            VARCHAR(200) NOT NULL,
    content          TEXT         NOT NULL,
    target_class_id  INTEGER,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcements_user  FOREIGN KEY (posted_by)       REFERENCES users (id),
    CONSTRAINT fk_announcements_class FOREIGN KEY (target_class_id) REFERENCES classes (id)
);
