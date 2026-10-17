package com.sms.auth;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import com.sms.common.FormException;
import com.sms.school.SchoolClassRepository;
import com.sms.admin.StudentForm;
import com.sms.admin.StudentAdminService;

@Controller
public class LoginController {

    private final StudentAdminService students;
    private final SchoolClassRepository classes;

    public LoginController(StudentAdminService students, SchoolClassRepository classes) {
        this.students = students;
        this.classes = classes;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("form", new StudentForm());
        return showSignup(model);
    }

    @PostMapping("/signup")
    public String register(@Valid @ModelAttribute("form") StudentForm form, BindingResult result,
                          Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return showSignup(model);
        try {
            students.create(form);
            redirect.addFlashAttribute("signupSuccess", "Your student account is ready. Sign in to continue.");
            return "redirect:/login";
        } catch (FormException e) {
            e.addTo(result);
            return showSignup(model);
        }
    }

    private String showSignup(Model model) {
        model.addAttribute("classes", classes.findAllByOrderByClassNameAscSectionAsc());
        return "signup";
    }

    /** "/" sends a logged-in user to their own dashboard. */
    @GetMapping("/")
    public String home(Authentication authentication) {
        return Role.from(authentication.getAuthorities())
                .map(role -> "redirect:" + role.dashboardUrl())
                .orElse("redirect:/login");
    }
}
