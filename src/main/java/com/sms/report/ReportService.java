package com.sms.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/** Read-only summaries built from attendance and grades. Callers do the access checks. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Present/absent counts; percent is null when nothing was recorded. */
    public record Tally(long present, long absent) {
        public Integer percent() {
            return ReportService.percent(present, present + absent);
        }
    }

    public record StudentAttendance(Student student, Tally tally) {
    }

    public record ClassAttendance(SchoolClass schoolClass, Tally tally, List<StudentAttendance> students) {
    }

    /** Average percentage and number of students graded, for one subject in one exam. */
    public record ExamStat(int averagePercent, int graded) {
    }

    public record SubjectPerformance(Subject subject, Map<ExamType, ExamStat> byExam) {
    }

    /** exams: only the exams that have any marks, in ExamType order. */
    public record ClassPerformance(List<ExamType> exams, List<SubjectPerformance> subjects) {
    }

    /** overallPercent per exam is total marks / total max marks. */
    public record ReportCard(Student student, Map<ExamType, List<Grade>> gradesByExam,
                             Map<ExamType, Integer> overallPercent, Tally attendance) {
    }

    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final AttendanceRepository attendance;
    private final GradeRepository grades;

    public ReportService(SchoolClassRepository classes, StudentRepository students, AttendanceRepository attendance,
                         GradeRepository grades) {
        this.classes = classes;
        this.students = students;
        this.attendance = attendance;
        this.grades = grades;
    }

    // ---- attendance ----

    /** One row per class, including classes with no records in the range. */
    public List<ClassAttendance> schoolAttendance(DateRange range) {
        Map<Long, long[]> byClass = new HashMap<>();
        for (Attendance a : attendance.findByDateBetween(range.from(), range.to())) {
            SchoolClass c = a.getStudent().getSchoolClass();
            if (c != null) {
                count(byClass.computeIfAbsent(c.getId(), k -> new long[2]), a);
            }
        }
        return classes.findAllByOrderByClassNameAscSectionAsc().stream()
                .map(c -> {
                    long[] t = byClass.getOrDefault(c.getId(), new long[2]);
                    return new ClassAttendance(c, new Tally(t[0], t[1]), List.of());
                })
                .toList();
    }

    /** One row per student in the class (in roll order), plus the class total. */
    public ClassAttendance classAttendance(SchoolClass schoolClass, DateRange range) {
        Map<Long, long[]> byStudent = new HashMap<>();
        long[] total = new long[2];
        for (Attendance a : attendance.findByStudentSchoolClassAndDateBetween(schoolClass, range.from(), range.to())) {
            count(byStudent.computeIfAbsent(a.getStudent().getId(), k -> new long[2]), a);
            count(total, a);
        }
        List<StudentAttendance> rows = students.findBySchoolClassOrderByRollNoAsc(schoolClass).stream()
                .map(s -> {
                    long[] t = byStudent.getOrDefault(s.getId(), new long[2]);
                    return new StudentAttendance(s, new Tally(t[0], t[1]));
                })
                .toList();
        return new ClassAttendance(schoolClass, new Tally(total[0], total[1]), rows);
    }

    // ---- performance ----

    /** Average % per subject per exam for the class; includeSubject limits which subjects appear. */
    public ClassPerformance classPerformance(SchoolClass schoolClass, Predicate<Subject> includeSubject) {
        Map<String, Subject> subjectsByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<Long, Map<ExamType, List<Integer>>> percents = new HashMap<>();
        for (Grade g : grades.findByStudentSchoolClass(schoolClass)) {
            if (!includeSubject.test(g.getSubject())) {
                continue;
            }
            subjectsByName.putIfAbsent(g.getSubject().getName(), g.getSubject());
            percents.computeIfAbsent(g.getSubject().getId(), k -> new EnumMap<>(ExamType.class))
                    .computeIfAbsent(g.getExamType(), k -> new ArrayList<>())
                    .add(g.getPercent());
        }
        List<ExamType> exams = new ArrayList<>();
        for (ExamType exam : ExamType.values()) {
            if (percents.values().stream().anyMatch(m -> m.containsKey(exam))) {
                exams.add(exam);
            }
        }
        List<SubjectPerformance> rows = subjectsByName.values().stream()
                .map(subject -> {
                    Map<ExamType, ExamStat> stats = new EnumMap<>(ExamType.class);
                    percents.get(subject.getId()).forEach((exam, list) -> stats.put(exam, new ExamStat(
                            (int) Math.round(list.stream().mapToInt(Integer::intValue).average().orElse(0)),
                            list.size())));
                    return new SubjectPerformance(subject, stats);
                })
                .toList();
        return new ClassPerformance(exams, rows);
    }

    // ---- report card ----

    public ReportCard reportCard(Student student) {
        Map<ExamType, List<Grade>> byExam = new LinkedHashMap<>();
        for (ExamType exam : ExamType.values()) {
            byExam.put(exam, new ArrayList<>());
        }
        for (Grade g : grades.findByStudentOrderBySubjectNameAsc(student)) {
            byExam.get(g.getExamType()).add(g);
        }
        byExam.values().removeIf(List::isEmpty);

        Map<ExamType, Integer> overall = new EnumMap<>(ExamType.class);
        byExam.forEach((exam, list) -> {
            BigDecimal marks = list.stream().map(Grade::getMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal max = list.stream().map(Grade::getMaxMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
            overall.put(exam, marks.multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.HALF_UP).intValue());
        });

        long[] t = new long[2];
        attendance.findByStudentOrderByDateDesc(student).forEach(a -> count(t, a));
        return new ReportCard(student, byExam, overall, new Tally(t[0], t[1]));
    }

    static Integer percent(long part, long total) {
        return total == 0 ? null : (int) Math.round(part * 100.0 / total);
    }

    /** t[0] = present, t[1] = absent */
    private static void count(long[] t, Attendance a) {
        t[a.getStatus() == AttendanceStatus.PRESENT ? 0 : 1]++;
    }
}
