package com.sms.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.common.FormException;

/** Creates and updates login accounts. Field names match the admin forms. */
@Service
@Transactional
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApiTokenService apiTokens;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, ApiTokenService apiTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.apiTokens = apiTokens;
    }

    public User create(String username, String email, String rawPassword, Role role) {
        if (users.existsByUsername(username)) {
            throw new FormException("username", "Username is already taken");
        }
        if (users.existsByEmail(email)) {
            throw new FormException("email", "Email is already in use");
        }
        checkPassword("password", rawPassword);
        return users.save(new User(username, email, passwordEncoder.encode(rawPassword), role));
    }

    /** newPassword blank = keep the current password. A new password signs the user out of the mobile app. */
    public void update(User user, String email, boolean enabled, String newPassword) {
        if (users.existsByEmailAndIdNot(email, user.getId())) {
            throw new FormException("email", "Email is already in use");
        }
        user.setEmail(email);
        user.setEnabled(enabled);
        if (newPassword != null && !newPassword.isBlank()) {
            checkPassword("password", newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
            apiTokens.revokeAll(user);
        }
    }

    /**
     * A user changing their own password; field names match the change-password form.
     * Also signs them out of the mobile app on every device.
     */
    public void changeOwnPassword(User user, String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new FormException("currentPassword", "Current password is incorrect");
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new FormException("confirmPassword", "Passwords don't match");
        }
        checkPassword("newPassword", newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        apiTokens.revokeAll(user);
    }

    private void checkPassword(String field, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new FormException(field, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }
}
