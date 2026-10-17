package com.sms.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.common.FormException;
import com.sms.common.InUseException;
import com.sms.school.Teacher;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/teachers")
public class AdminTeacherController {

    private static final String FORM = "admin/teachers/form";

    private final TeacherAdminService service;

    public AdminTeacherController(TeacherAdminService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("teachers", service.findAll());
        return "admin/teachers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new TeacherForm());
        return FORM;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") TeacherForm form, BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return FORM;
        }
        try {
            Teacher teacher = service.create(form);
            redirect.addFlashAttribute("success", "Teacher " + teacher.getName() + " added.");
            return "redirect:/admin/teachers";
        } catch (FormException e) {
            e.addTo(result);
            return FORM;
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", TeacherForm.from(service.get(id)));
        return FORM;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") TeacherForm form, BindingResult result,
                         RedirectAttributes redirect) {
        form.setId(id);
        if (result.hasErrors()) {
            return FORM;
        }
        try {
            service.update(id, form);
            redirect.addFlashAttribute("success", "Teacher " + form.getName().trim() + " updated.");
            return "redirect:/admin/teachers";
        } catch (FormException e) {
            e.addTo(result);
            return FORM;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.delete(id);
            redirect.addFlashAttribute("success", "Teacher deleted.");
        } catch (InUseException e) {
            redirect.addFlashAttribute("error",
                    "This teacher is still linked to classes, subjects, attendance, grades or announcements. "
                            + "Remove those links first, or disable the account instead.");
        }
        return "redirect:/admin/teachers";
    }
}
