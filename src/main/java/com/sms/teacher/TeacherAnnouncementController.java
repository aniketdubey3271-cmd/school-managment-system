package com.sms.teacher;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sms.announcement.AnnouncementForm;
import com.sms.announcement.AnnouncementService;
import com.sms.common.FormException;
import com.sms.school.Teacher;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/teacher/announcements")
public class TeacherAnnouncementController {

    private final AnnouncementService service;
    private final TeacherPortalService portal;

    public TeacherAnnouncementController(AnnouncementService service, TeacherPortalService portal) {
        this.service = service;
        this.portal = portal;
    }

    @GetMapping
    public String page(Principal principal, Model model) {
        model.addAttribute("form", new AnnouncementForm());
        return show(teacher(principal), model);
    }

    @PostMapping
    public String post(@Valid @ModelAttribute("form") AnnouncementForm form, BindingResult result, Principal principal,
                       Model model, RedirectAttributes redirect) {
        Teacher teacher = teacher(principal);
        if (result.hasErrors()) {
            return show(teacher, model);
        }
        try {
            service.postAsTeacher(teacher, classIds(teacher), form);
        } catch (FormException e) {
            e.addTo(result);
            return show(teacher, model);
        }
        redirect.addFlashAttribute("success", "Announcement posted.");
        return "redirect:/teacher/announcements";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        service.deleteOwn(teacher(principal).getUser(), id);
        redirect.addFlashAttribute("success", "Announcement deleted.");
        return "redirect:/teacher/announcements";
    }

    private String show(Teacher teacher, Model model) {
        List<MyClass> myClasses = portal.myClasses(teacher);
        model.addAttribute("myClasses", myClasses);
        model.addAttribute("views", service.forTeacher(teacher, ids(myClasses)));
        model.addAttribute("me", teacher.getUser().getId());
        return "teacher/announcements";
    }

    private Set<Long> classIds(Teacher teacher) {
        return ids(portal.myClasses(teacher));
    }

    private static Set<Long> ids(List<MyClass> myClasses) {
        return myClasses.stream().map(mc -> mc.schoolClass().getId()).collect(Collectors.toSet());
    }

    private Teacher teacher(Principal principal) {
        return portal.currentTeacher(principal.getName());
    }
}
