package com.sms.teacher;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.common.FormException;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.Teacher;
import com.sms.school.TeachingAssignment;

@Controller
@RequestMapping("/teacher/classes")
public class TeacherClassController {

    private static final BigDecimal DEFAULT_MAX_MARKS = new BigDecimal("100");

    private final TeacherPortalService service;

    public TeacherClassController(TeacherPortalService service) {
        this.service = service;
    }

    @GetMapping
    public String myClasses(Principal principal, Model model) {
        model.addAttribute("myClasses", service.myClasses(teacher(principal)));
        return "teacher/classes";
    }

    @GetMapping("/{classId}/students")
    public String students(@PathVariable Long classId, Principal principal, Model model) {
        SchoolClass schoolClass = service.requireClassAccess(teacher(principal), classId);
        model.addAttribute("schoolClass", schoolClass);
        model.addAttribute("students", service.students(schoolClass));
        return "teacher/students";
    }

    // ---- attendance (class teacher only) ----

    @GetMapping("/{classId}/attendance")
    public String attendance(@PathVariable Long classId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             Principal principal, Model model) {
        SchoolClass schoolClass = service.requireClassTeacher(teacher(principal), classId);
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<Long, AttendanceStatus> saved = service.attendanceFor(schoolClass, day);
        List<Student> students = service.students(schoolClass);

        AttendanceForm form = new AttendanceForm();
        form.setDate(day);
        for (Student s : students) {
            form.getStatuses().put(s.getId(), saved.getOrDefault(s.getId(), AttendanceStatus.PRESENT));
        }
        model.addAttribute("alreadyMarked", !saved.isEmpty());
        return showAttendance(schoolClass, students, form, model);
    }

    @PostMapping("/{classId}/attendance")
    public String saveAttendance(@PathVariable Long classId, @ModelAttribute("form") AttendanceForm form,
                                 Principal principal, Model model, RedirectAttributes redirect) {
        Teacher teacher = teacher(principal);
        SchoolClass schoolClass = service.requireClassTeacher(teacher, classId);
        try {
            service.saveAttendance(teacher, schoolClass, form.getDate(), form.getStatuses());
        } catch (FormException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("alreadyMarked", false);
            return showAttendance(schoolClass, service.students(schoolClass), form, model);
        }
        redirect.addFlashAttribute("success", "Attendance saved for " + form.getDate() + ".");
        return "redirect:/teacher/classes/" + classId + "/attendance?date=" + form.getDate();
    }

    private String showAttendance(SchoolClass schoolClass, List<Student> students, AttendanceForm form, Model model) {
        model.addAttribute("schoolClass", schoolClass);
        model.addAttribute("students", students);
        model.addAttribute("form", form);
        model.addAttribute("today", LocalDate.now());
        return "teacher/attendance";
    }

    // ---- grades (subject teacher only) ----

    @GetMapping("/{classId}/subjects/{subjectId}/grades")
    public String grades(@PathVariable Long classId, @PathVariable Long subjectId,
                         @RequestParam(defaultValue = "UNIT_TEST_1") ExamType exam, Principal principal, Model model) {
        TeachingAssignment assignment = service.requireTeaches(teacher(principal), classId, subjectId);
        Map<Long, Grade> saved = service.gradesFor(assignment.getSchoolClass(), assignment.getSubject(), exam);

        GradeForm form = new GradeForm();
        form.setExamType(exam);
        form.setMaxMarks(saved.values().stream().findFirst().map(Grade::getMaxMarks).orElse(DEFAULT_MAX_MARKS));
        Map<Long, BigDecimal> marks = new HashMap<>();
        saved.forEach((studentId, grade) -> marks.put(studentId, grade.getMarks()));
        form.setMarks(marks);
        return showGrades(assignment, form, model);
    }

    @PostMapping("/{classId}/subjects/{subjectId}/grades")
    public String saveGrades(@PathVariable Long classId, @PathVariable Long subjectId,
                             @ModelAttribute("form") GradeForm form, Principal principal, Model model,
                             RedirectAttributes redirect) {
        Teacher teacher = teacher(principal);
        TeachingAssignment assignment = service.requireTeaches(teacher, classId, subjectId);
        try {
            service.saveGrades(teacher, assignment, form.getExamType(), form.getMaxMarks(), form.getMarks());
        } catch (FormException e) {
            model.addAttribute("error", e.getMessage());
            return showGrades(assignment, form, model);
        }
        redirect.addFlashAttribute("success", form.getExamType().getLabel() + " marks saved.");
        return "redirect:/teacher/classes/" + classId + "/subjects/" + subjectId + "/grades?exam=" + form.getExamType();
    }

    private String showGrades(TeachingAssignment assignment, GradeForm form, Model model) {
        model.addAttribute("assignment", assignment);
        model.addAttribute("students", service.students(assignment.getSchoolClass()));
        model.addAttribute("examTypes", ExamType.values());
        model.addAttribute("form", form);
        return "teacher/grades";
    }

    private Teacher teacher(Principal principal) {
        return service.currentTeacher(principal.getName());
    }
}
