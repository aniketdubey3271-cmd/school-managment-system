package com.sms.admin;

import java.security.Principal;

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
import com.sms.auth.User;
import com.sms.auth.UserRepository;
import com.sms.common.FormException;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/announcements")
public class AdminAnnouncementController {

    private final AnnouncementService service;
    private final ClassAdminService classService;
    private final UserRepository users;

    public AdminAnnouncementController(AnnouncementService service, ClassAdminService classService,
                                       UserRepository users) {
        this.service = service;
        this.classService = classService;
        this.users = users;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("form", new AnnouncementForm());
        return show(model);
    }

    @PostMapping
    public String post(@Valid @ModelAttribute("form") AnnouncementForm form, BindingResult result, Principal principal,
                       Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return show(model);
        }
        try {
            service.postAsAdmin(currentUser(principal), form);
        } catch (FormException e) {
            e.addTo(result);
            return show(model);
        }
        redirect.addFlashAttribute("success", "Announcement posted.");
        return "redirect:/admin/announcements";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        service.deleteAsAdmin(id);
        redirect.addFlashAttribute("success", "Announcement deleted.");
        return "redirect:/admin/announcements";
    }

    private String show(Model model) {
        model.addAttribute("views", service.forAdmin());
        model.addAttribute("classes", classService.findAllClasses());
        return "admin/announcements";
    }

    private User currentUser(Principal principal) {
        return users.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
    }
}
