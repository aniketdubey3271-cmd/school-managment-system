package com.sms.teacher;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.sms.report.DateRange;
import com.sms.report.ReportService;
import com.sms.school.SchoolClass;
import com.sms.school.Subject;
import com.sms.school.Teacher;

/**
 * Class report for a teacher: attendance for the whole class, and performance for the
 * subjects they teach there (all subjects if they are the class teacher).
 */
@Controller
public class TeacherReportController {

    private final TeacherPortalService portal;
    private final ReportService reports;

    public TeacherReportController(TeacherPortalService portal, ReportService reports) {
        this.portal = portal;
        this.reports = reports;
    }

    @GetMapping("/teacher/classes/{classId}/report")
    public String report(@PathVariable Long classId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         Principal principal, Model model) {
        Teacher teacher = portal.currentTeacher(principal.getName());
        SchoolClass schoolClass = portal.requireClassAccess(teacher, classId);
        MyClass mine = portal.myClasses(teacher).stream()
                .filter(mc -> mc.schoolClass().getId().equals(classId))
                .findFirst().orElseThrow();
        Set<Long> taught = mine.subjects().stream().map(Subject::getId).collect(Collectors.toSet());
        Predicate<Subject> visible = mine.classTeacher() ? s -> true : s -> taught.contains(s.getId());

        DateRange range = DateRange.of(from, to);
        model.addAttribute("range", range);
        model.addAttribute("attendance", reports.classAttendance(schoolClass, range));
        model.addAttribute("performance", reports.classPerformance(schoolClass, visible));
        model.addAttribute("allSubjects", mine.classTeacher());
        return "teacher/report";
    }
}
