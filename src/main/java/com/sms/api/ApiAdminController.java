package com.sms.api;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sms.admin.ClassAdminService;
import com.sms.admin.StudentAdminService;
import com.sms.admin.TeacherAdminService;
import com.sms.announcement.AnnouncementForm;
import com.sms.announcement.AnnouncementService;
import com.sms.api.ApiModels.AnnouncementInfo;
import com.sms.api.ApiModels.ClassRef;
import com.sms.api.ApiModels.ClassReport;
import com.sms.api.ApiModels.ClassStudent;
import com.sms.api.ApiModels.SchoolAttendanceReport;
import com.sms.api.ApiModels.StudentInfo;
import com.sms.api.ApiModels.SubjectRef;
import com.sms.api.ApiModels.TeacherInfo;
import com.sms.api.ApiModels.TeacherRef;
import com.sms.api.ApiModels.TeachingInfo;
import com.sms.auth.User;
import com.sms.auth.UserRepository;
import com.sms.common.Downloads;
import com.sms.report.DateRange;
import com.sms.report.PdfService;
import com.sms.report.ReportService;
import com.sms.school.SchoolClass;
import com.sms.school.Student;

import jakarta.validation.Valid;

/**
 * Admin views for the app: school data (read-only), announcements, reports and PDFs.
 * Adding and editing people, classes and subjects stays on the website.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class ApiAdminController {

    public record Overview(long students, long teachers, long classes, long subjects) {
    }

    public record ClassSummary(ClassRef schoolClass, TeacherRef classTeacher, long studentCount) {
    }

    public record ClassDetail(ClassRef schoolClass, TeacherRef classTeacher, List<ClassStudent> students,
                              List<TeachingInfo> subjectTeachers) {
    }

    private final StudentAdminService students;
    private final TeacherAdminService teachers;
    private final ClassAdminService classes;
    private final AnnouncementService announcements;
    private final ReportService reports;
    private final PdfService pdfs;
    private final UserRepository users;

    public ApiAdminController(StudentAdminService students, TeacherAdminService teachers, ClassAdminService classes,
                              AnnouncementService announcements, ReportService reports, PdfService pdfs,
                              UserRepository users) {
        this.students = students;
        this.teachers = teachers;
        this.classes = classes;
        this.announcements = announcements;
        this.reports = reports;
        this.pdfs = pdfs;
        this.users = users;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return new Overview(students.find(null).size(), teachers.findAll().size(), classes.findAllClasses().size(),
                classes.findAllSubjects().size());
    }

    // ---- school data ----

    /** classId optional: all students when absent. */
    @GetMapping("/students")
    public List<StudentInfo> students(@RequestParam(required = false) Long classId) {
        return students.find(classId).stream().map(StudentInfo::from).toList();
    }

    @GetMapping("/students/{id}")
    public StudentInfo student(@PathVariable Long id) {
        return StudentInfo.from(students.get(id));
    }

    @GetMapping("/teachers")
    public List<TeacherInfo> teachers() {
        return teachers.findAll().stream().map(TeacherInfo::from).toList();
    }

    @GetMapping("/teachers/{id}")
    public TeacherInfo teacher(@PathVariable Long id) {
        return TeacherInfo.from(teachers.get(id));
    }

    @GetMapping("/classes")
    public List<ClassSummary> classes() {
        return classes.findAllClasses().stream()
                .map(c -> new ClassSummary(ClassRef.from(c), TeacherRef.from(c.getClassTeacher()),
                        classes.countStudents(c)))
                .toList();
    }

    @GetMapping("/classes/{id}")
    public ClassDetail schoolClass(@PathVariable Long id) {
        SchoolClass c = classes.getClass(id);
        return new ClassDetail(ClassRef.from(c), TeacherRef.from(c.getClassTeacher()),
                students.find(id).stream().map(ClassStudent::from).toList(),
                classes.assignmentsFor(c).stream().map(TeachingInfo::from).toList());
    }

    @GetMapping("/subjects")
    public List<SubjectRef> subjects() {
        return classes.findAllSubjects().stream().map(SubjectRef::from).toList();
    }

    // ---- announcements ----

    @GetMapping("/announcements")
    public List<AnnouncementInfo> announcements(Principal principal) {
        Long me = currentUser(principal).getId();
        return announcements.forAdmin().stream().map(v -> AnnouncementInfo.from(v, me)).toList();
    }

    /** targetClassId null = school-wide. */
    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementInfo postAnnouncement(@Valid @RequestBody AnnouncementForm form, Principal principal) {
        User admin = currentUser(principal);
        var posted = announcements.postAsAdmin(admin, form);
        return AnnouncementInfo.from(new AnnouncementService.View(posted, "School office"), admin.getId());
    }

    @DeleteMapping("/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnnouncement(@PathVariable Long id) {
        announcements.deleteAsAdmin(id);
    }

    // ---- reports ----

    /** Attendance per class; the range defaults to the last 30 days. */
    @GetMapping("/reports/attendance")
    public SchoolAttendanceReport schoolAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange range = DateRange.of(from, to);
        return SchoolAttendanceReport.from(range, reports.schoolAttendance(range));
    }

    @GetMapping("/reports/classes/{id}")
    public ClassReport classReport(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SchoolClass c = classes.getClass(id);
        DateRange range = DateRange.of(from, to);
        return ClassReport.from(c, range, reports.classAttendance(c, range), reports.classPerformance(c, s -> true));
    }

    @GetMapping("/students/{id}/report-card")
    public ResponseEntity<Resource> reportCard(@PathVariable Long id) {
        Student s = students.get(id);
        return Downloads.pdf(pdfs.reportCard(s), "report-card-" + s.getUser().getUsername() + ".pdf");
    }

    @GetMapping("/students/{id}/id-card")
    public ResponseEntity<Resource> idCard(@PathVariable Long id) {
        Student s = students.get(id);
        return Downloads.pdf(pdfs.idCard(s), "id-card-" + s.getUser().getUsername() + ".pdf");
    }

    private User currentUser(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow();
    }
}
