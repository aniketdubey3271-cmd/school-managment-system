package com.sms.api;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sms.announcement.AnnouncementForm;
import com.sms.announcement.AnnouncementService;
import com.sms.api.ApiModels.AnnouncementInfo;
import com.sms.api.ApiModels.AssignmentInfo;
import com.sms.api.ApiModels.ClassRef;
import com.sms.api.ApiModels.ClassReport;
import com.sms.api.ApiModels.ClassStudent;
import com.sms.api.ApiModels.ExamTypeInfo;
import com.sms.api.ApiModels.MyClassInfo;
import com.sms.api.ApiModels.SubjectRef;
import com.sms.api.ApiModels.SubmissionInfo;
import com.sms.api.ApiModels.TeachingInfo;
import com.sms.common.Downloads;
import com.sms.common.FileStorage;
import com.sms.report.DateRange;
import com.sms.report.ReportService;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.SchoolClass;
import com.sms.school.Submission;
import com.sms.school.Teacher;
import com.sms.school.TeachingAssignment;
import com.sms.teacher.AssignmentForm;
import com.sms.teacher.TeacherAssignmentService;
import com.sms.teacher.TeacherPortalService;

import jakarta.validation.Valid;

/**
 * The teacher's classes, attendance, grades, assignments and announcements. Access rules are the
 * website's: any assigned teacher can view a class, only the class teacher marks attendance,
 * and only a subject's teacher enters its grades.
 */
@RestController
@RequestMapping("/api/v1/teacher")
public class ApiTeacherController {

    private static final BigDecimal DEFAULT_MAX_MARKS = new BigDecimal("100");

    /** status is PRESENT or ABSENT; unmarked students default to PRESENT, as on the website. */
    public record AttendanceEntry(ClassStudent student, String status) {
    }

    public record AttendanceSheet(ClassRef schoolClass, LocalDate date, boolean alreadyMarked,
                                  List<AttendanceEntry> students) {
    }

    /** statuses: studentId -> PRESENT/ABSENT. Students left out are not changed. */
    public record AttendanceUpdate(LocalDate date, Map<Long, AttendanceStatus> statuses) {
    }

    /** marks is null when not entered yet. */
    public record GradeEntry(ClassStudent student, BigDecimal marks) {
    }

    public record GradeSheet(ClassRef schoolClass, SubjectRef subject, ExamTypeInfo exam, BigDecimal maxMarks,
                             List<GradeEntry> students, List<ExamTypeInfo> examTypes) {
    }

    /** marks: studentId -> marks. Students left out keep their current marks. */
    public record GradeUpdate(ExamType exam, BigDecimal maxMarks, Map<Long, BigDecimal> marks) {
    }

    public record AssignmentRow(AssignmentInfo assignment, long submitted, long classSize) {
    }

    public record SubmissionRow(ClassStudent student, SubmissionInfo submission) {
    }

    public record AssignmentDetail(AssignmentInfo assignment, List<SubmissionRow> submissions) {
    }

    private final TeacherPortalService portal;
    private final TeacherAssignmentService assignments;
    private final ReportService reports;
    private final AnnouncementService announcements;
    private final FileStorage files;

    public ApiTeacherController(TeacherPortalService portal, TeacherAssignmentService assignments,
                                ReportService reports, AnnouncementService announcements, FileStorage files) {
        this.portal = portal;
        this.assignments = assignments;
        this.reports = reports;
        this.announcements = announcements;
        this.files = files;
    }

    // ---- classes ----

    @GetMapping("/classes")
    public List<MyClassInfo> classes(Principal principal) {
        return portal.myClasses(teacher(principal)).stream().map(MyClassInfo::from).toList();
    }

    /** The class+subject pairs this teacher teaches; a new assignment names one by id. */
    @GetMapping("/teaching")
    public List<TeachingInfo> teaching(Principal principal) {
        return assignments.teachable(teacher(principal)).stream().map(TeachingInfo::from).toList();
    }

    @GetMapping("/classes/{classId}/students")
    public List<ClassStudent> students(@PathVariable Long classId, Principal principal) {
        SchoolClass schoolClass = portal.requireClassAccess(teacher(principal), classId);
        return portal.students(schoolClass).stream().map(ClassStudent::from).toList();
    }

    @GetMapping("/classes/{classId}/report")
    public ClassReport report(@PathVariable Long classId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              Principal principal) {
        Teacher teacher = teacher(principal);
        SchoolClass schoolClass = portal.requireClassAccess(teacher, classId);
        DateRange range = DateRange.of(from, to);
        return ClassReport.from(schoolClass, range, reports.classAttendance(schoolClass, range),
                reports.classPerformance(schoolClass, portal.reportSubjects(teacher, schoolClass)));
    }

    // ---- attendance (class teacher only) ----

    @GetMapping("/classes/{classId}/attendance")
    public AttendanceSheet attendance(@PathVariable Long classId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      Principal principal) {
        SchoolClass schoolClass = portal.requireClassTeacher(teacher(principal), classId);
        return attendanceSheet(schoolClass, date == null ? LocalDate.now() : date);
    }

    @PutMapping("/classes/{classId}/attendance")
    public AttendanceSheet saveAttendance(@PathVariable Long classId, @RequestBody AttendanceUpdate update,
                                          Principal principal) {
        Teacher teacher = teacher(principal);
        SchoolClass schoolClass = portal.requireClassTeacher(teacher, classId);
        portal.saveAttendance(teacher, schoolClass, update.date(),
                update.statuses() == null ? Map.of() : update.statuses());
        return attendanceSheet(schoolClass, update.date());
    }

    private AttendanceSheet attendanceSheet(SchoolClass schoolClass, LocalDate date) {
        Map<Long, AttendanceStatus> saved = portal.attendanceFor(schoolClass, date);
        List<AttendanceEntry> rows = portal.students(schoolClass).stream()
                .map(s -> new AttendanceEntry(ClassStudent.from(s),
                        saved.getOrDefault(s.getId(), AttendanceStatus.PRESENT).name()))
                .toList();
        return new AttendanceSheet(ClassRef.from(schoolClass), date, !saved.isEmpty(), rows);
    }

    // ---- grades (subject teacher only) ----

    @GetMapping("/classes/{classId}/subjects/{subjectId}/grades")
    public GradeSheet grades(@PathVariable Long classId, @PathVariable Long subjectId,
                             @RequestParam(defaultValue = "UNIT_TEST_1") ExamType exam, Principal principal) {
        return gradeSheet(portal.requireTeaches(teacher(principal), classId, subjectId), exam);
    }

    @PutMapping("/classes/{classId}/subjects/{subjectId}/grades")
    public GradeSheet saveGrades(@PathVariable Long classId, @PathVariable Long subjectId,
                                 @RequestBody GradeUpdate update, Principal principal) {
        Teacher teacher = teacher(principal);
        TeachingAssignment teaching = portal.requireTeaches(teacher, classId, subjectId);
        portal.saveGrades(teacher, teaching, update.exam(), update.maxMarks(),
                update.marks() == null ? Map.of() : update.marks());
        return gradeSheet(teaching, update.exam());
    }

    private GradeSheet gradeSheet(TeachingAssignment teaching, ExamType exam) {
        Map<Long, Grade> saved = portal.gradesFor(teaching.getSchoolClass(), teaching.getSubject(), exam);
        BigDecimal maxMarks = saved.values().stream().findFirst().map(Grade::getMaxMarks).orElse(DEFAULT_MAX_MARKS);
        List<GradeEntry> rows = portal.students(teaching.getSchoolClass()).stream()
                .map(s -> new GradeEntry(ClassStudent.from(s),
                        saved.containsKey(s.getId()) ? saved.get(s.getId()).getMarks() : null))
                .toList();
        return new GradeSheet(ClassRef.from(teaching.getSchoolClass()), SubjectRef.from(teaching.getSubject()),
                ExamTypeInfo.from(exam), maxMarks, rows, ExamTypeInfo.all());
    }

    // ---- assignments ----

    @GetMapping("/assignments")
    public List<AssignmentRow> assignments(Principal principal) {
        return assignments.mine(teacher(principal)).stream()
                .map(r -> new AssignmentRow(AssignmentInfo.from(r.assignment()), r.submitted(), r.classSize()))
                .toList();
    }

    /** Body: teachingId (from /teaching), title, description, dueDate (ISO, e.g. 2026-11-30T17:00:00). */
    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentInfo createAssignment(@Valid @RequestBody AssignmentForm form, Principal principal) {
        return AssignmentInfo.from(assignments.create(teacher(principal), form));
    }

    @GetMapping("/assignments/{id}")
    public AssignmentDetail assignment(@PathVariable Long id, Principal principal) {
        var assignment = assignments.requireOwn(teacher(principal), id);
        return new AssignmentDetail(AssignmentInfo.from(assignment), assignments.submissions(assignment).stream()
                .map(r -> new SubmissionRow(ClassStudent.from(r.student()), SubmissionInfo.from(r.submission())))
                .toList());
    }

    /** Title, description and due date can change; class and subject can't. */
    @PutMapping("/assignments/{id}")
    public AssignmentInfo updateAssignment(@PathVariable Long id, @Valid @RequestBody AssignmentForm form,
                                           Principal principal) {
        Teacher teacher = teacher(principal);
        assignments.update(teacher, id, form);
        return AssignmentInfo.from(assignments.requireOwn(teacher, id));
    }

    /** 409 if students have already submitted. */
    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable Long id, Principal principal) {
        assignments.delete(teacher(principal), id);
    }

    @GetMapping("/assignments/{id}/submissions/{submissionId}/file")
    public ResponseEntity<Resource> submissionFile(@PathVariable Long id, @PathVariable Long submissionId,
                                                   Principal principal) {
        Submission s = assignments.requireSubmission(assignments.requireOwn(teacher(principal), id), submissionId);
        return Downloads.attachment(files.load(s.getStoredName()), s.getOriginalFilename());
    }

    // ---- announcements ----

    /** School-wide notices, notices for the teacher's classes, and their own posts. */
    @GetMapping("/announcements")
    public List<AnnouncementInfo> announcements(Principal principal) {
        Teacher teacher = teacher(principal);
        Long me = teacher.getUser().getId();
        return announcements.forTeacher(teacher, classIds(teacher)).stream()
                .map(v -> AnnouncementInfo.from(v, me)).toList();
    }

    /** targetClassId must be one of the teacher's classes. */
    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementInfo postAnnouncement(@Valid @RequestBody AnnouncementForm form, Principal principal) {
        Teacher teacher = teacher(principal);
        var posted = announcements.postAsTeacher(teacher, classIds(teacher), form);
        return AnnouncementInfo.from(new AnnouncementService.View(posted, teacher.getName()), teacher.getUser().getId());
    }

    /** Only the teacher's own announcements. */
    @DeleteMapping("/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnnouncement(@PathVariable Long id, Principal principal) {
        announcements.deleteOwn(teacher(principal).getUser(), id);
    }

    private Set<Long> classIds(Teacher teacher) {
        return portal.myClasses(teacher).stream().map(mc -> mc.schoolClass().getId()).collect(Collectors.toSet());
    }

    private Teacher teacher(Principal principal) {
        return portal.currentTeacher(principal.getName());
    }
}
