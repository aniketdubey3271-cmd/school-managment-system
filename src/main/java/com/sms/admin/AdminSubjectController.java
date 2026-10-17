package com.sms.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.common.FormException;
import com.sms.common.InUseException;

@Controller
@RequestMapping("/admin/subjects")
public class AdminSubjectController {

    private final ClassAdminService service;

    public AdminSubjectController(ClassAdminService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("subjects", service.findAllSubjects());
        return "admin/subjects/list";
    }

    @PostMapping
    public String create(@RequestParam String name, RedirectAttributes redirect) {
        try {
            service.createSubject(name);
            redirect.addFlashAttribute("success", "Subject added.");
        } catch (FormException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/subjects";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.deleteSubject(id);
            redirect.addFlashAttribute("success", "Subject deleted.");
        } catch (InUseException e) {
            redirect.addFlashAttribute("error",
                    "This subject is assigned to a class or already has grades, so it can't be deleted.");
        }
        return "redirect:/admin/subjects";
    }
}
