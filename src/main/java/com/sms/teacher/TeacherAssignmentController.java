package com.sms.teacher;

import java.security.Principal;
import java.time.LocalDateTime;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.common.Downloads;
import com.sms.common.FileStorage;
import com.sms.common.FormException;
import com.sms.common.InUseException;
import com.sms.school.Assignment;
import com.sms.school.Submission;
import com.sms.school.Teacher;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/teacher/assignments")
public class TeacherAssignmentController {

    private static final String FORM = "teacher/assignments/form";

    private final TeacherAssignmentService service;
    private final TeacherPortalService portal;
    private final FileStorage files;

    public TeacherAssignmentController(TeacherAssignmentService service, TeacherPortalService portal, FileStorage files) {
        this.service = service;
        this.portal = portal;
        this.files = files;
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("rows", service.mine(teacher(principal)));
        model.addAttribute("now", LocalDateTime.now());
        return "teacher/assignments/list";
    }

    @GetMapping("/new")
    public String newForm(Principal principal, Model model) {
        model.addAttribute("form", new AssignmentForm());
        return showForm(teacher(principal), model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AssignmentForm form, BindingResult result, Principal principal,
                         Model model, RedirectAttributes redirect) {
        Teacher teacher = teacher(principal);
        if (result.hasErrors()) {
            return showForm(teacher, model);
        }
        try {
            Assignment a = service.create(teacher, form);
            redirect.addFlashAttribute("success", "Assignment created for " + a.getSchoolClass().getDisplayName() + ".");
            return "redirect:/teacher/assignments/" + a.getId();
        } catch (FormException e) {
            e.addTo(result);
            return showForm(teacher, model);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        Assignment a = service.requireOwn(teacher(principal), id);
        model.addAttribute("assignment", a);
        model.addAttribute("rows", service.submissions(a));
        model.addAttribute("now", LocalDateTime.now());
        return "teacher/assignments/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        Teacher teacher = teacher(principal);
        Assignment a = service.requireOwn(teacher, id);
        model.addAttribute("form", AssignmentForm.from(a));
        model.addAttribute("assignment", a);
        return showForm(teacher, model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") AssignmentForm form, BindingResult result,
                         Principal principal, Model model, RedirectAttributes redirect) {
        Teacher teacher = teacher(principal);
        Assignment a = service.requireOwn(teacher, id);
        form.setId(id);
        if (result.hasErrors()) {
            model.addAttribute("assignment", a);
            return showForm(teacher, model);
        }
        service.update(teacher, id, form);
        redirect.addFlashAttribute("success", "Assignment updated.");
        return "redirect:/teacher/assignments/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            service.delete(teacher(principal), id);
            redirect.addFlashAttribute("success", "Assignment deleted.");
            return "redirect:/teacher/assignments";
        } catch (InUseException e) {
            redirect.addFlashAttribute("error", "Students have already submitted work for this assignment, so it can't be deleted.");
            return "redirect:/teacher/assignments/" + id;
        }
    }

    @GetMapping("/{id}/submissions/{submissionId}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long submissionId, Principal principal) {
        Submission s = service.requireSubmission(service.requireOwn(teacher(principal), id), submissionId);
        return Downloads.attachment(files.load(s.getStoredName()), s.getOriginalFilename());
    }

    private String showForm(Teacher teacher, Model model) {
        model.addAttribute("teachable", service.teachable(teacher));
        return FORM;
    }

    private Teacher teacher(Principal principal) {
        return portal.currentTeacher(principal.getName());
    }
}
