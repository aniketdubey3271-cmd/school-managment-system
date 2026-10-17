package com.sms.auth;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** "/" sends a logged-in user to their own dashboard. */
    @GetMapping("/")
    public String home(Authentication authentication) {
        return Role.from(authentication.getAuthorities())
                .map(role -> "redirect:" + role.dashboardUrl())
                .orElse("redirect:/login");
    }
}
