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
import com.sms.school.SchoolClass;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/classes")
public class AdminClassController {

    private static final String FORM = "admin/classes/form";

    private final ClassAdminService service;
    private final TeacherAdminService teacherService;
    private final StudentAdminService studentService;

    public AdminClassController(ClassAdminService service, TeacherAdminService teacherService,
                                StudentAdminService studentService) {
        this.service = service;
        this.teacherService = teacherService;
        this.studentService = studentService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("classes", service.findAllClasses());
        return "admin/classes/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        SchoolClass schoolClass = service.getClass(id);
        model.addAttribute("schoolClass", schoolClass);
        model.addAttribute("students", studentService.find(id));
        model.addAttribute("assignments", service.assignmentsFor(schoolClass));
        model.addAttribute("subjects", service.findAllSubjects());
        model.addAttribute("teachers", teacherService.findAll());
        return "admin/classes/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ClassForm());
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ClassForm form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return showForm(model);
        }
        try {
            SchoolClass schoolClass = service.createClass(form);
            redirect.addFlashAttribute("success", "Class " + schoolClass.getDisplayName() + " created.");
            return "redirect:/admin/classes/" + schoolClass.getId();
        } catch (FormException e) {
            e.addTo(result);
            return showForm(model);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", ClassForm.from(service.getClass(id)));
        return showForm(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ClassForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (result.hasErrors()) {
            return showForm(model);
        }
        try {
            service.updateClass(id, form);
            redirect.addFlashAttribute("success", "Class updated.");
            return "redirect:/admin/classes/" + id;
        } catch (FormException e) {
            e.addTo(result);
            return showForm(model);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.deleteClass(id);
            redirect.addFlashAttribute("success", "Class deleted.");
            return "redirect:/admin/classes";
        } catch (InUseException e) {
            redirect.addFlashAttribute("error",
                    "This class still has students, subject teachers or other records. Move or remove them first.");
            return "redirect:/admin/classes/" + id;
        }
    }

    @PostMapping("/{id}/assignments")
    public String assign(@PathVariable Long id, @RequestParam(required = false) Long subjectId,
                         @RequestParam(required = false) Long teacherId, RedirectAttributes redirect) {
        try {
            service.assign(id, subjectId, teacherId);
            redirect.addFlashAttribute("success", "Teacher assigned.");
        } catch (FormException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/classes/" + id;
    }

    @PostMapping("/{id}/assignments/{assignmentId}/delete")
    public String unassign(@PathVariable Long id, @PathVariable Long assignmentId, RedirectAttributes redirect) {
        service.unassign(id, assignmentId);
        redirect.addFlashAttribute("success", "Assignment removed.");
        return "redirect:/admin/classes/" + id;
    }

    private String showForm(Model model) {
        model.addAttribute("teachers", teacherService.findAll());
        return FORM;
    }
}
