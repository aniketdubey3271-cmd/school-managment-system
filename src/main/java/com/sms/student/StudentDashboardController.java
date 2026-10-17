package com.sms.student;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.announcement.AnnouncementService;
import com.sms.auth.AccountService;
import com.sms.common.FormException;
import com.sms.school.Attendance;
import com.sms.school.Student;

/** Student dashboard, profile, attendance, grades and announcements: all read-only except the student's own password. */
@Controller
@RequestMapping("/student")
public class StudentDashboardController {

    private final StudentPortalService service;
    private final AccountService accounts;
    private final AnnouncementService announcements;

    public StudentDashboardController(StudentPortalService service, AccountService accounts,
                                      AnnouncementService announcements) {
        this.service = service;
        this.accounts = accounts;
        this.announcements = announcements;
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        Student student = service.currentStudent(principal.getName());
        model.addAttribute("student", student);
        model.addAttribute("attendanceSummary", service.summarize(service.attendance(student)));
        model.addAttribute("pendingAssignments", service.assignments(student).stream()
                .filter(r -> !r.pastDue() && r.submission() == null).count());
        model.addAttribute("latestAnnouncements", announcements.forStudent(student, 3));
        return "student/dashboard";
    }

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("student", service.currentStudent(principal.getName()));
        model.addAttribute("form", new PasswordForm());
        return "student/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@ModelAttribute("form") PasswordForm form, BindingResult result, Principal principal,
                                 Model model, RedirectAttributes redirect) {
        Student student = service.currentStudent(principal.getName());
        try {
            accounts.changeOwnPassword(student.getUser(), form.getCurrentPassword(), form.getNewPassword(),
                    form.getConfirmPassword());
        } catch (FormException e) {
            e.addTo(result);
            model.addAttribute("student", student);
            return "student/profile";
        }
        redirect.addFlashAttribute("success", "Password changed.");
        return "redirect:/student/profile";
    }

    @GetMapping("/attendance")
    public String attendance(Principal principal, Model model) {
        List<Attendance> records = service.attendance(service.currentStudent(principal.getName()));
        model.addAttribute("records", records);
        model.addAttribute("summary", service.summarize(records));
        return "student/attendance";
    }

    @GetMapping("/announcements")
    public String announcements(Principal principal, Model model) {
        model.addAttribute("views", announcements.forStudent(service.currentStudent(principal.getName()), null));
        return "student/announcements";
    }

    @GetMapping("/grades")
    public String grades(Principal principal, Model model) {
        model.addAttribute("gradesByExam", service.gradesByExam(service.currentStudent(principal.getName())));
        return "student/grades";
    }
}
