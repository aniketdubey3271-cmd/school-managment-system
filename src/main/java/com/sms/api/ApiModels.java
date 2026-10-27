package com.sms.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.sms.announcement.AnnouncementService;
import com.sms.auth.ApiTokenService;
import com.sms.auth.User;
import com.sms.report.DateRange;
import com.sms.report.PdfService;
import com.sms.report.ReportService;
import com.sms.school.Assignment;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.Subject;
import com.sms.school.Submission;
import com.sms.school.Teacher;
import com.sms.school.TeachingAssignment;
import com.sms.teacher.MyClass;

import jakarta.validation.constraints.NotBlank;

/** JSON shapes sent to the mobile app. Entities are never serialized directly. */
public final class ApiModels {

    private ApiModels() {
    }

    // ---- auth ----

    /** login is a username or an email, as on the website. */
    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                                UserInfo user) {
        static TokenResponse from(ApiTokenService.Issued issued) {
            return new TokenResponse(issued.accessToken(), issued.refreshToken(), "Bearer", issued.expiresIn(),
                    UserInfo.from(issued.user()));
        }
    }

    public record UserInfo(Long id, String username, String email, String role) {
        static UserInfo from(User u) {
            return new UserInfo(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name());
        }
    }

    public record SignupOptions(List<ClassRef> classes, boolean teacherSignupEnabled, boolean adminSignupEnabled) {
    }

    /** The signed-in user; student or teacher is set according to the role. */
    public record Me(UserInfo user, StudentInfo student, TeacherInfo teacher) {
    }

    // ---- references ----

    public record ClassRef(Long id, String name) {
        static ClassRef from(SchoolClass c) {
            return c == null ? null : new ClassRef(c.getId(), c.getDisplayName());
        }
    }

    public record SubjectRef(Long id, String name) {
        static SubjectRef from(Subject s) {
            return s == null ? null : new SubjectRef(s.getId(), s.getName());
        }
    }

    public record TeacherRef(Long id, String name) {
        static TeacherRef from(Teacher t) {
            return t == null ? null : new TeacherRef(t.getId(), t.getName());
        }
    }

    public record ExamTypeInfo(String code, String label) {
        static ExamTypeInfo from(ExamType e) {
            return new ExamTypeInfo(e.name(), e.getLabel());
        }

        static List<ExamTypeInfo> all() {
            return List.of(ExamType.values()).stream().map(ExamTypeInfo::from).toList();
        }
    }

    // ---- people ----

    public record StudentInfo(Long id, String studentId, String name, String rollNo, ClassRef schoolClass,
                              LocalDate dob, String address, String parentContact, String username, String email,
                              boolean enabled) {
        static StudentInfo from(Student s) {
            return new StudentInfo(s.getId(), PdfService.idNumber(s), s.getName(), s.getRollNo(),
                    ClassRef.from(s.getSchoolClass()), s.getDob(), s.getAddress(), s.getParentContact(),
                    s.getUser().getUsername(), s.getUser().getEmail(), s.getUser().isEnabled());
        }
    }

    public record TeacherInfo(Long id, String name, String subjectSpecialization, String contact, String username,
                              String email, boolean enabled) {
        static TeacherInfo from(Teacher t) {
            return new TeacherInfo(t.getId(), t.getName(), t.getSubjectSpecialization(), t.getContact(),
                    t.getUser().getUsername(), t.getUser().getEmail(), t.getUser().isEnabled());
        }
    }

    /** A student as listed for a class. */
    public record ClassStudent(Long id, String name, String rollNo) {
        static ClassStudent from(Student s) {
            return new ClassStudent(s.getId(), s.getName(), s.getRollNo());
        }
    }

    // ---- announcements ----

    public record AnnouncementInfo(Long id, String title, String content, ClassRef targetClass, String author,
                                   LocalDateTime createdAt, boolean mine) {
        static AnnouncementInfo from(AnnouncementService.View v, Long currentUserId) {
            var a = v.announcement();
            return new AnnouncementInfo(a.getId(), a.getTitle(), a.getContent(), ClassRef.from(a.getTargetClass()),
                    v.author(), a.getCreatedAt(), a.getPostedBy().getId().equals(currentUserId));
        }
    }

    // ---- attendance and grades ----

    /** percent is null when nothing was recorded. */
    public record Tally(long present, long absent, Integer percent) {
        static Tally from(ReportService.Tally t) {
            return new Tally(t.present(), t.absent(), t.percent());
        }
    }

    public record AttendanceRecord(LocalDate date, String status) {
    }

    public record StudentAttendance(Tally summary, List<AttendanceRecord> records) {
    }

    public record GradeInfo(SubjectRef subject, BigDecimal marks, BigDecimal maxMarks, int percent) {
        static GradeInfo from(Grade g) {
            return new GradeInfo(SubjectRef.from(g.getSubject()), g.getMarks(), g.getMaxMarks(), g.getPercent());
        }
    }

    public record ExamGrades(ExamTypeInfo exam, List<GradeInfo> grades) {
        static List<ExamGrades> from(Map<ExamType, List<Grade>> byExam) {
            return byExam.entrySet().stream()
                    .map(e -> new ExamGrades(ExamTypeInfo.from(e.getKey()),
                            e.getValue().stream().map(GradeInfo::from).toList()))
                    .toList();
        }
    }

    // ---- assignments ----

    public record AssignmentInfo(Long id, String title, String description, ClassRef schoolClass, SubjectRef subject,
                                 TeacherRef teacher, LocalDateTime dueDate, boolean pastDue) {
        static AssignmentInfo from(Assignment a) {
            return new AssignmentInfo(a.getId(), a.getTitle(), a.getDescription(), ClassRef.from(a.getSchoolClass()),
                    SubjectRef.from(a.getSubject()), TeacherRef.from(a.getTeacher()), a.getDueDate(),
                    a.isPastDue(LocalDateTime.now()));
        }
    }

    public record SubmissionInfo(Long id, String originalFilename, LocalDateTime submittedAt) {
        static SubmissionInfo from(Submission s) {
            return s == null ? null : new SubmissionInfo(s.getId(), s.getOriginalFilename(), s.getSubmittedAt());
        }
    }

    // ---- reports ----

    public record ClassAttendanceRow(ClassRef schoolClass, Tally tally) {
    }

    public record SchoolAttendanceReport(LocalDate from, LocalDate to, List<ClassAttendanceRow> classes) {
        static SchoolAttendanceReport from(DateRange range, List<ReportService.ClassAttendance> rows) {
            return new SchoolAttendanceReport(range.from(), range.to(), rows.stream()
                    .map(r -> new ClassAttendanceRow(ClassRef.from(r.schoolClass()), Tally.from(r.tally())))
                    .toList());
        }
    }

    public record StudentAttendanceRow(ClassStudent student, Tally tally) {
    }

    public record ExamStat(ExamTypeInfo exam, int averagePercent, int graded) {
    }

    public record SubjectPerformance(SubjectRef subject, List<ExamStat> exams) {
    }

    /** Attendance for the whole class plus average marks per subject and exam. */
    public record ClassReport(ClassRef schoolClass, LocalDate from, LocalDate to, Tally attendance,
                              List<StudentAttendanceRow> students, List<ExamTypeInfo> exams,
                              List<SubjectPerformance> subjects) {
        static ClassReport from(SchoolClass c, DateRange range, ReportService.ClassAttendance attendance,
                                ReportService.ClassPerformance performance) {
            return new ClassReport(ClassRef.from(c), range.from(), range.to(), Tally.from(attendance.tally()),
                    attendance.students().stream()
                            .map(s -> new StudentAttendanceRow(ClassStudent.from(s.student()), Tally.from(s.tally())))
                            .toList(),
                    performance.exams().stream().map(ExamTypeInfo::from).toList(),
                    performance.subjects().stream()
                            .map(p -> new SubjectPerformance(SubjectRef.from(p.subject()), p.byExam().entrySet().stream()
                                    .map(e -> new ExamStat(ExamTypeInfo.from(e.getKey()), e.getValue().averagePercent(),
                                            e.getValue().graded()))
                                    .toList()))
                            .toList());
        }
    }

    // ---- teaching ----

    public record MyClassInfo(ClassRef schoolClass, boolean classTeacher, List<SubjectRef> subjects) {
        static MyClassInfo from(MyClass mc) {
            return new MyClassInfo(ClassRef.from(mc.schoolClass()), mc.classTeacher(),
                    mc.subjects().stream().map(SubjectRef::from).toList());
        }
    }

    /** One class+subject pair and who teaches it; id is what a new assignment refers to. */
    public record TeachingInfo(Long id, ClassRef schoolClass, SubjectRef subject, TeacherRef teacher) {
        static TeachingInfo from(TeachingAssignment t) {
            return new TeachingInfo(t.getId(), ClassRef.from(t.getSchoolClass()), SubjectRef.from(t.getSubject()),
                    TeacherRef.from(t.getTeacher()));
        }
    }
}
