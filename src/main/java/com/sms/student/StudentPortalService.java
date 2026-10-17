package com.sms.student;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sms.common.FileStorage;
import com.sms.common.FormException;
import com.sms.common.NotFoundException;
import com.sms.school.Assignment;
import com.sms.school.AssignmentRepository;
import com.sms.school.Attendance;
import com.sms.school.AttendanceRepository;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.GradeRepository;
import com.sms.school.Student;
import com.sms.school.StudentRepository;
import com.sms.school.Submission;
import com.sms.school.SubmissionRepository;

/** Read-only views of a student's own data, plus submitting assignments. Never takes another student's id. */
@Service
@Transactional(readOnly = true)
public class StudentPortalService {

    public record AttendanceSummary(long present, long absent, Integer percent) {
    }

    public record AssignmentRow(Assignment assignment, Submission submission, boolean pastDue) {
    }

    private final StudentRepository students;
    private final AttendanceRepository attendance;
    private final GradeRepository grades;
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final FileStorage files;

    public StudentPortalService(StudentRepository students, AttendanceRepository attendance, GradeRepository grades,
                                AssignmentRepository assignments, SubmissionRepository submissions, FileStorage files) {
        this.students = students;
        this.attendance = attendance;
        this.grades = grades;
        this.assignments = assignments;
        this.submissions = submissions;
        this.files = files;
    }

    public Student currentStudent(String username) {
        return students.findByUserUsername(username)
                .orElseThrow(() -> new AccessDeniedException("No student profile for " + username));
    }

    // ---- attendance ----

    public List<Attendance> attendance(Student student) {
        return attendance.findByStudentOrderByDateDesc(student);
    }

    public AttendanceSummary summarize(List<Attendance> records) {
        long present = records.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count();
        long absent = records.size() - present;
        Integer percent = records.isEmpty() ? null : (int) Math.round(present * 100.0 / records.size());
        return new AttendanceSummary(present, absent, percent);
    }

    // ---- grades ----

    /** Exams in ExamType order, each with its grades sorted by subject. */
    public Map<ExamType, List<Grade>> gradesByExam(Student student) {
        Map<ExamType, List<Grade>> byExam = new LinkedHashMap<>();
        for (ExamType exam : ExamType.values()) {
            byExam.put(exam, new ArrayList<>());
        }
        for (Grade g : grades.findByStudentOrderBySubjectNameAsc(student)) {
            byExam.get(g.getExamType()).add(g);
        }
        byExam.values().removeIf(List::isEmpty);
        return byExam;
    }

    // ---- assignments ----

    /** Open assignments first (soonest due), then past-due ones (most recent first). */
    public List<AssignmentRow> assignments(Student student) {
        if (student.getSchoolClass() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Submission> mine = submissions.findByStudent(student).stream()
                .collect(Collectors.toMap(s -> s.getAssignment().getId(), Function.identity()));
        List<AssignmentRow> rows = new ArrayList<>();
        for (Assignment a : assignments.findBySchoolClassOrderByDueDateAsc(student.getSchoolClass())) {
            rows.add(new AssignmentRow(a, mine.get(a.getId()), a.isPastDue(now)));
        }
        rows.sort(Comparator.comparing(AssignmentRow::pastDue)
                .thenComparing(r -> r.pastDue() ? r.assignment().getDueDate().toString() : "", Comparator.reverseOrder()));
        return rows;
    }

    /** The assignment, only if it was set for the student's own class. */
    public Assignment requireAssignment(Student student, Long assignmentId) {
        Assignment a = assignments.findById(assignmentId).orElseThrow(() -> new NotFoundException("Assignment"));
        if (student.getSchoolClass() == null || !a.getSchoolClass().getId().equals(student.getSchoolClass().getId())) {
            throw new AccessDeniedException("Not your class's assignment");
        }
        return a;
    }

    public Optional<Submission> submission(Student student, Assignment assignment) {
        return submissions.findByAssignmentAndStudent(assignment, student);
    }

    /** Uploads or replaces the student's file. Refused after the due date. */
    @Transactional
    public Submission submit(Student student, Assignment assignment, MultipartFile file) {
        if (assignment.isPastDue(LocalDateTime.now())) {
            throw new FormException("The due date has passed; submissions are closed.");
        }
        String storedName = files.store(file);
        Submission submission = submissions.findByAssignmentAndStudent(assignment, student).orElseGet(() -> {
            Submission s = new Submission();
            s.setAssignment(assignment);
            s.setStudent(student);
            return s;
        });
        if (submission.getStoredName() != null) {
            files.deleteAfterCommit(submission.getStoredName());
        }
        submission.setStoredName(storedName);
        submission.setOriginalFilename(cleanFilename(file.getOriginalFilename()));
        submission.setSubmittedAt(LocalDateTime.now());
        return submissions.save(submission);
    }

    /** Keeps only the last path segment and caps the length, for display and the download name. */
    static String cleanFilename(String name) {
        if (name == null || name.isBlank()) {
            return "submission";
        }
        String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).trim();
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }
}
