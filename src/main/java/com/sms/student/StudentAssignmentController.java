package com.sms.student;

import java.security.Principal;
import java.time.LocalDateTime;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.common.Downloads;
import com.sms.common.FileStorage;
import com.sms.common.FormException;
import com.sms.common.NotFoundException;
import com.sms.school.Assignment;
import com.sms.school.Student;
import com.sms.school.Submission;

@Controller
@RequestMapping("/student/assignments")
public class StudentAssignmentController {

    private final StudentPortalService service;
    private final FileStorage files;

    public StudentAssignmentController(StudentPortalService service, FileStorage files) {
        this.service = service;
        this.files = files;
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("rows", service.assignments(service.currentStudent(principal.getName())));
        return "student/assignments/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        Student student = service.currentStudent(principal.getName());
        Assignment assignment = service.requireAssignment(student, id);
        model.addAttribute("assignment", assignment);
        model.addAttribute("submission", service.submission(student, assignment).orElse(null));
        model.addAttribute("pastDue", assignment.isPastDue(LocalDateTime.now()));
        model.addAttribute("allowedTypes", String.join(", ", FileStorage.ALLOWED_EXTENSIONS.stream().sorted().toList()));
        return "student/assignments/detail";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, @RequestParam(required = false) MultipartFile file, Principal principal,
                         RedirectAttributes redirect) {
        Student student = service.currentStudent(principal.getName());
        Assignment assignment = service.requireAssignment(student, id);
        try {
            service.submit(student, assignment, file);
            redirect.addFlashAttribute("success", "Submitted.");
        } catch (FormException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/assignments/" + id;
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id, Principal principal) {
        Student student = service.currentStudent(principal.getName());
        Submission submission = service.submission(student, service.requireAssignment(student, id))
                .orElseThrow(() -> new NotFoundException("Submission"));
        return Downloads.attachment(files.load(submission.getStoredName()), submission.getOriginalFilename());
    }
}
