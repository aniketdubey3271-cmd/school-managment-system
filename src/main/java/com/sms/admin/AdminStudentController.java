package com.sms.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.common.FormException;
import com.sms.common.InUseException;
import com.sms.school.Student;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/students")
public class AdminStudentController {

    private static final String FORM = "admin/students/form";

    private final StudentAdminService service;
    private final ClassAdminService classService;

    public AdminStudentController(StudentAdminService service, ClassAdminService classService) {
        this.service = service;
        this.classService = classService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long classId, Model model) {
        model.addAttribute("students", service.find(classId));
        model.addAttribute("classes", classService.findAllClasses());
        model.addAttribute("classId", classId);
        return "admin/students/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long classId, Model model) {
        StudentForm form = new StudentForm();
        form.setClassId(classId);
        model.addAttribute("form", form);
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StudentForm form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return showForm(model);
        }
        try {
            Student student = service.create(form);
            redirect.addFlashAttribute("success", "Student " + student.getName() + " added.");
            return "redirect:/admin/students";
        } catch (FormException e) {
            e.addTo(result);
            return showForm(model);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", StudentForm.from(service.get(id)));
        return showForm(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") StudentForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (result.hasErrors()) {
            return showForm(model);
        }
        try {
            service.update(id, form);
            redirect.addFlashAttribute("success", "Student " + form.getName().trim() + " updated.");
            return "redirect:/admin/students";
        } catch (FormException e) {
            e.addTo(result);
            return showForm(model);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.delete(id);
            redirect.addFlashAttribute("success", "Student deleted.");
        } catch (InUseException e) {
            redirect.addFlashAttribute("error",
                    "This student already has attendance, grades or submissions. Disable the account instead.");
        }
        return "redirect:/admin/students";
    }

    private String showForm(Model model) {
        model.addAttribute("classes", classService.findAllClasses());
        return FORM;
    }
}
