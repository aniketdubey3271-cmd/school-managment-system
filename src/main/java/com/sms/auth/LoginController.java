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

@Controller
public class LoginController {

    private final SignupService signups;
    private final SchoolClassRepository classes;

    public LoginController(SignupService signups, SchoolClassRepository classes) {
        this.signups = signups;
        this.classes = classes;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("form", new SignupForm());
        return showSignup(model);
    }

    @PostMapping("/signup")
    public String register(@Valid @ModelAttribute("form") SignupForm form, BindingResult result,
                          Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return showSignup(model);
        try {
            Role role = signups.register(form).getRole();
            redirect.addFlashAttribute("signupSuccess", "Your " + role.name().toLowerCase()
                    + " account is ready. Sign in to continue.");
            return "redirect:/login";
        } catch (FormException e) {
            e.addTo(result);
            return showSignup(model);
        }
    }

    private String showSignup(Model model) {
        model.addAttribute("classes", classes.findAllByOrderByClassNameAscSectionAsc());
        model.addAttribute("teacherSignupEnabled", signups.teacherSignupEnabled());
        model.addAttribute("adminSignupEnabled", signups.adminSignupEnabled());
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
