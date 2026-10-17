package com.sms.teacher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.common.FormException;
import com.sms.common.NotFoundException;
import com.sms.school.Attendance;
import com.sms.school.AttendanceRepository;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.GradeRepository;
import com.sms.school.SchoolClass;
import com.sms.school.SchoolClassRepository;
import com.sms.school.Student;
import com.sms.school.StudentRepository;
import com.sms.school.Subject;
import com.sms.school.Teacher;
import com.sms.school.TeacherRepository;
import com.sms.school.TeachingAssignment;
import com.sms.school.TeachingAssignmentRepository;

/**
 * Everything a teacher can do, scoped to their own classes:
 * any assigned teacher can view a class, only the class teacher marks attendance,
 * and only the subject's teacher enters its grades.
 */
@Service
@Transactional
public class TeacherPortalService {

    private static final BigDecimal MAX_ALLOWED_MARKS = new BigDecimal("999");

    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;
    private final TeachingAssignmentRepository assignments;
    private final StudentRepository students;
    private final AttendanceRepository attendance;
    private final GradeRepository grades;

    public TeacherPortalService(TeacherRepository teachers, SchoolClassRepository classes,
                                TeachingAssignmentRepository assignments, StudentRepository students,
                                AttendanceRepository attendance, GradeRepository grades) {
        this.teachers = teachers;
        this.classes = classes;
        this.assignments = assignments;
        this.students = students;
        this.attendance = attendance;
        this.grades = grades;
    }

    @Transactional(readOnly = true)
    public Teacher currentTeacher(String username) {
        return teachers.findByUserUsername(username)
                .orElseThrow(() -> new AccessDeniedException("No teacher profile for " + username));
    }

    @Transactional(readOnly = true)
    public List<MyClass> myClasses(Teacher teacher) {
        Map<Long, SchoolClass> byId = new LinkedHashMap<>();
        Map<Long, List<Subject>> subjectsByClass = new LinkedHashMap<>();
        for (SchoolClass c : classes.findByClassTeacher(teacher)) {
            byId.put(c.getId(), c);
        }
        for (TeachingAssignment a : assignments.findByTeacher(teacher)) {
            byId.putIfAbsent(a.getSchoolClass().getId(), a.getSchoolClass());
            subjectsByClass.computeIfAbsent(a.getSchoolClass().getId(), k -> new ArrayList<>()).add(a.getSubject());
        }
        return byId.values().stream()
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .map(c -> new MyClass(c, isClassTeacher(teacher, c), subjectsByClass.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    // ---- access checks ----

    /** Class teacher or any subject teacher of the class. */
    @Transactional(readOnly = true)
    public SchoolClass requireClassAccess(Teacher teacher, Long classId) {
        SchoolClass schoolClass = getClass(classId);
        if (!isClassTeacher(teacher, schoolClass) && !assignments.existsByTeacherAndSchoolClass(teacher, schoolClass)) {
            throw new AccessDeniedException("Not your class");
        }
        return schoolClass;
    }

    @Transactional(readOnly = true)
    public SchoolClass requireClassTeacher(Teacher teacher, Long classId) {
        SchoolClass schoolClass = getClass(classId);
        if (!isClassTeacher(teacher, schoolClass)) {
            throw new AccessDeniedException("Only the class teacher can mark attendance");
        }
        return schoolClass;
    }

    @Transactional(readOnly = true)
    public TeachingAssignment requireTeaches(Teacher teacher, Long classId, Long subjectId) {
        return assignments.findBySchoolClassIdAndSubjectId(classId, subjectId)
                .filter(a -> a.getTeacher().getId().equals(teacher.getId()))
                .orElseThrow(() -> new AccessDeniedException("You don't teach this subject in this class"));
    }

    // ---- students ----

    @Transactional(readOnly = true)
    public List<Student> students(SchoolClass schoolClass) {
        return students.findBySchoolClassOrderByRollNoAsc(schoolClass);
    }

    // ---- attendance ----

    @Transactional(readOnly = true)
    public Map<Long, AttendanceStatus> attendanceFor(SchoolClass schoolClass, LocalDate date) {
        return attendance.findByStudentSchoolClassAndDate(schoolClass, date).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Attendance::getStatus));
    }

    /** Creates or updates one record per student. Ids not in this class are ignored. */
    public void saveAttendance(Teacher teacher, SchoolClass schoolClass, LocalDate date,
                               Map<Long, AttendanceStatus> statuses) {
        if (date == null) {
            throw new FormException("Pick a date");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new FormException("Attendance can't be marked for a future date");
        }
        for (Student student : students(schoolClass)) {
            AttendanceStatus status = statuses.get(student.getId());
            if (status == null) {
                continue;
            }
            Attendance record = attendance.findByStudentAndDate(student, date).orElseGet(() -> {
                Attendance a = new Attendance();
                a.setStudent(student);
                a.setDate(date);
                return a;
            });
            record.setStatus(status);
            record.setMarkedBy(teacher);
            attendance.save(record);
        }
    }

    // ---- grades ----

    @Transactional(readOnly = true)
    public Map<Long, Grade> gradesFor(SchoolClass schoolClass, Subject subject, ExamType examType) {
        return grades.findByStudentSchoolClassAndSubjectAndExamType(schoolClass, subject, examType).stream()
                .collect(Collectors.toMap(g -> g.getStudent().getId(), g -> g));
    }

    /** Validates every entry first, then saves; blank marks are skipped. Ids not in this class are ignored. */
    public void saveGrades(Teacher teacher, TeachingAssignment assignment, ExamType examType,
                           BigDecimal maxMarks, Map<Long, BigDecimal> marks) {
        if (examType == null) {
            throw new FormException("Pick an exam");
        }
        if (maxMarks == null || maxMarks.signum() <= 0 || maxMarks.compareTo(MAX_ALLOWED_MARKS) > 0) {
            throw new FormException("Max marks must be between 1 and " + MAX_ALLOWED_MARKS);
        }
        List<Student> classStudents = students(assignment.getSchoolClass());
        for (Student student : classStudents) {
            BigDecimal value = marks.get(student.getId());
            if (value != null && (value.signum() < 0 || value.compareTo(maxMarks) > 0)) {
                throw new FormException("Marks for " + student.getName() + " must be between 0 and "
                        + maxMarks.stripTrailingZeros().toPlainString());
            }
        }
        Subject subject = assignment.getSubject();
        for (Student student : classStudents) {
            BigDecimal value = marks.get(student.getId());
            if (value == null) {
                continue;
            }
            Grade grade = grades.findByStudentAndSubjectAndExamType(student, subject, examType).orElseGet(() -> {
                Grade g = new Grade();
                g.setStudent(student);
                g.setSubject(subject);
                g.setExamType(examType);
                return g;
            });
            grade.setMarks(value);
            grade.setMaxMarks(maxMarks);
            grade.setEnteredBy(teacher);
            grades.save(grade);
        }
    }

    private SchoolClass getClass(Long classId) {
        return classes.findById(classId).orElseThrow(() -> new NotFoundException("Class"));
    }

    private static boolean isClassTeacher(Teacher teacher, SchoolClass schoolClass) {
        return schoolClass.getClassTeacher() != null && schoolClass.getClassTeacher().getId().equals(teacher.getId());
    }
}
