package com.sms.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin account on startup if none exists. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder,
                       @Value("${sms.admin.username}") String username,
                       @Value("${sms.admin.email}") String email,
                       @Value("${sms.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("No admin account exists and SMS_ADMIN_PASSWORD is not set; skipping admin creation.");
            return;
        }
        users.save(new User(username, email, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Created admin account '{}'.", username);
    }
}
