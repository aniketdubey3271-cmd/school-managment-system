package com.sms.teacher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.common.FormException;
import com.sms.common.InUseException;
import com.sms.common.NotFoundException;
import com.sms.school.Assignment;
import com.sms.school.AssignmentRepository;
import com.sms.school.Student;
import com.sms.school.StudentRepository;
import com.sms.school.Submission;
import com.sms.school.SubmissionRepository;
import com.sms.school.Teacher;
import com.sms.school.TeachingAssignment;
import com.sms.school.TeachingAssignmentRepository;

/** Assignments a teacher sets, scoped to the class+subject pairs they teach. */
@Service
@Transactional
public class TeacherAssignmentService {

    public record AssignmentRow(Assignment assignment, long submitted, long classSize) {
    }

    /** One row per student in the class; submission is null if they haven't submitted. */
    public record SubmissionRow(Student student, Submission submission) {
    }

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final TeachingAssignmentRepository teaching;
    private final StudentRepository students;

    public TeacherAssignmentService(AssignmentRepository assignments, SubmissionRepository submissions,
                                    TeachingAssignmentRepository teaching, StudentRepository students) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.teaching = teaching;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<TeachingAssignment> teachable(Teacher teacher) {
        return teaching.findByTeacher(teacher);
    }

    @Transactional(readOnly = true)
    public List<AssignmentRow> mine(Teacher teacher) {
        return assignments.findByTeacherOrderByDueDateDesc(teacher).stream()
                .map(a -> new AssignmentRow(a, submissions.countByAssignment(a),
                        students.countBySchoolClass(a.getSchoolClass())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Assignment requireOwn(Teacher teacher, Long assignmentId) {
        Assignment a = assignments.findById(assignmentId).orElseThrow(() -> new NotFoundException("Assignment"));
        if (!a.getTeacher().getId().equals(teacher.getId())) {
            throw new AccessDeniedException("Not your assignment");
        }
        return a;
    }

    public Assignment create(Teacher teacher, AssignmentForm form) {
        if (form.getTeachingId() == null) {
            throw new FormException("teachingId", "Select a class and subject");
        }
        TeachingAssignment pair = teaching.findById(form.getTeachingId())
                .filter(t -> t.getTeacher().getId().equals(teacher.getId()))
                .orElseThrow(() -> new FormException("teachingId", "You don't teach that class and subject"));
        if (!form.getDueDate().isAfter(LocalDateTime.now())) {
            throw new FormException("dueDate", "Due date must be in the future");
        }
        Assignment a = new Assignment();
        a.setTeacher(teacher);
        a.setSchoolClass(pair.getSchoolClass());
        a.setSubject(pair.getSubject());
        apply(form, a);
        return assignments.save(a);
    }

    /** Class and subject can't change; title, description and due date can. */
    public void update(Teacher teacher, Long id, AssignmentForm form) {
        apply(form, requireOwn(teacher, id));
    }

    /** Throws InUseException if students have already submitted. */
    public void delete(Teacher teacher, Long id) {
        assignments.delete(requireOwn(teacher, id));
        InUseException.flushDeletes(assignments);
    }

    @Transactional(readOnly = true)
    public List<SubmissionRow> submissions(Assignment assignment) {
        Map<Long, Submission> byStudent = submissions.findByAssignment(assignment).stream()
                .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));
        return students.findBySchoolClassOrderByRollNoAsc(assignment.getSchoolClass()).stream()
                .map(s -> new SubmissionRow(s, byStudent.get(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Submission requireSubmission(Assignment assignment, Long submissionId) {
        return submissions.findById(submissionId)
                .filter(s -> s.getAssignment().getId().equals(assignment.getId()))
                .orElseThrow(() -> new NotFoundException("Submission"));
    }

    private static void apply(AssignmentForm form, Assignment a) {
        a.setTitle(form.getTitle().trim());
        a.setDescription(form.getDescription() == null || form.getDescription().isBlank() ? null : form.getDescription().trim());
        a.setDueDate(form.getDueDate());
    }
}
