package com.sms.api;

import java.security.Principal;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sms.announcement.AnnouncementService;
import com.sms.api.ApiModels.AnnouncementInfo;
import com.sms.api.ApiModels.AssignmentInfo;
import com.sms.api.ApiModels.AttendanceRecord;
import com.sms.api.ApiModels.ExamGrades;
import com.sms.api.ApiModels.StudentAttendance;
import com.sms.api.ApiModels.StudentInfo;
import com.sms.api.ApiModels.SubmissionInfo;
import com.sms.api.ApiModels.Tally;
import com.sms.common.Downloads;
import com.sms.common.FileStorage;
import com.sms.common.NotFoundException;
import com.sms.report.PdfService;
import com.sms.school.Assignment;
import com.sms.school.Attendance;
import com.sms.school.Student;
import com.sms.school.Submission;
import com.sms.student.StudentPortalService;

/** The student's own data only: every call starts from the signed-in student, never from an id in the URL. */
@RestController
@RequestMapping("/api/v1/student")
public class ApiStudentController {

    public record Dashboard(StudentInfo student, Tally attendance, long pendingAssignments,
                            List<AnnouncementInfo> latestAnnouncements) {
    }

    public record StudentAssignment(AssignmentInfo assignment, SubmissionInfo submission) {
    }

    public record StudentAssignmentDetail(AssignmentInfo assignment, SubmissionInfo submission,
                                          List<String> allowedFileTypes, long maxFileBytes) {
    }

    private final StudentPortalService service;
    private final AnnouncementService announcements;
    private final PdfService pdfs;
    private final FileStorage files;

    public ApiStudentController(StudentPortalService service, AnnouncementService announcements, PdfService pdfs,
                                FileStorage files) {
        this.service = service;
        this.announcements = announcements;
        this.pdfs = pdfs;
        this.files = files;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(Principal principal) {
        Student student = student(principal);
        long pending = service.assignments(student).stream()
                .filter(r -> !r.pastDue() && r.submission() == null).count();
        return new Dashboard(StudentInfo.from(student), tally(service.summarize(service.attendance(student))),
                pending, announcementsFor(student, 3));
    }

    @GetMapping("/profile")
    public StudentInfo profile(Principal principal) {
        return StudentInfo.from(student(principal));
    }

    @GetMapping("/attendance")
    public StudentAttendance attendance(Principal principal) {
        List<Attendance> records = service.attendance(student(principal));
        return new StudentAttendance(tally(service.summarize(records)), records.stream()
                .map(a -> new AttendanceRecord(a.getDate(), a.getStatus().name()))
                .toList());
    }

    @GetMapping("/grades")
    public List<ExamGrades> grades(Principal principal) {
        return ExamGrades.from(service.gradesByExam(student(principal)));
    }

    @GetMapping("/announcements")
    public List<AnnouncementInfo> announcements(Principal principal) {
        return announcementsFor(student(principal), null);
    }

    /** Open assignments first (soonest due), then past-due ones. */
    @GetMapping("/assignments")
    public List<StudentAssignment> assignments(Principal principal) {
        return service.assignments(student(principal)).stream()
                .map(r -> new StudentAssignment(AssignmentInfo.from(r.assignment()), SubmissionInfo.from(r.submission())))
                .toList();
    }

    @GetMapping("/assignments/{id}")
    public StudentAssignmentDetail assignment(@PathVariable Long id, Principal principal) {
        Student student = student(principal);
        Assignment assignment = service.requireAssignment(student, id);
        return detail(assignment, service.submission(student, assignment).orElse(null));
    }

    /** Uploads or replaces the file (multipart field "file"). Same size and type limits as the website. */
    @PostMapping(path = "/assignments/{id}/submission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StudentAssignmentDetail submit(@PathVariable Long id, @RequestParam(required = false) MultipartFile file,
                                          Principal principal) {
        Student student = student(principal);
        Assignment assignment = service.requireAssignment(student, id);
        return detail(assignment, service.submit(student, assignment, file));
    }

    @GetMapping("/assignments/{id}/submission/file")
    public ResponseEntity<Resource> submissionFile(@PathVariable Long id, Principal principal) {
        Student student = student(principal);
        Submission submission = service.submission(student, service.requireAssignment(student, id))
                .orElseThrow(() -> new NotFoundException("Submission"));
        return Downloads.attachment(files.load(submission.getStoredName()), submission.getOriginalFilename());
    }

    @GetMapping("/report-card")
    public ResponseEntity<Resource> reportCard(Principal principal) {
        return Downloads.pdf(pdfs.reportCard(student(principal)), "report-card.pdf");
    }

    @GetMapping("/id-card")
    public ResponseEntity<Resource> idCard(Principal principal) {
        return Downloads.pdf(pdfs.idCard(student(principal)), "id-card.pdf");
    }

    private StudentAssignmentDetail detail(Assignment assignment, Submission submission) {
        return new StudentAssignmentDetail(AssignmentInfo.from(assignment), SubmissionInfo.from(submission),
                FileStorage.ALLOWED_EXTENSIONS.stream().sorted().toList(), FileStorage.MAX_BYTES);
    }

    private List<AnnouncementInfo> announcementsFor(Student student, Integer limit) {
        Long me = student.getUser().getId();
        return announcements.forStudent(student, limit).stream().map(v -> AnnouncementInfo.from(v, me)).toList();
    }

    private static Tally tally(StudentPortalService.AttendanceSummary summary) {
        return new Tally(summary.present(), summary.absent(), summary.percent());
    }

    private Student student(Principal principal) {
        return service.currentStudent(principal.getName());
    }
}
