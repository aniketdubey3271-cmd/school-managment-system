package com.sms.api;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sms.api.ApiModels.LoginRequest;
import com.sms.api.ApiModels.Me;
import com.sms.api.ApiModels.RefreshRequest;
import com.sms.api.ApiModels.SignupOptions;
import com.sms.api.ApiModels.StudentInfo;
import com.sms.api.ApiModels.TeacherInfo;
import com.sms.api.ApiModels.TokenResponse;
import com.sms.api.ApiModels.UserInfo;
import com.sms.auth.AccountService;
import com.sms.auth.ApiTokenService;
import com.sms.auth.SignupForm;
import com.sms.auth.SignupService;
import com.sms.auth.User;
import com.sms.auth.UserRepository;
import com.sms.school.SchoolClassRepository;
import com.sms.school.StudentRepository;
import com.sms.school.TeacherRepository;
import com.sms.student.PasswordForm;

import jakarta.validation.Valid;

/** Sign-in, sign-up and the signed-in user's own account. */
@RestController
@RequestMapping("/api/v1")
public class ApiAuthController {

    private final ApiTokenService tokens;
    private final SignupService signups;
    private final AccountService accounts;
    private final UserRepository users;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;

    public ApiAuthController(ApiTokenService tokens, SignupService signups, AccountService accounts,
                             UserRepository users, StudentRepository students, TeacherRepository teachers,
                             SchoolClassRepository classes) {
        this.tokens = tokens;
        this.signups = signups;
        this.accounts = accounts;
        this.users = users;
        this.students = students;
        this.teachers = teachers;
        this.classes = classes;
    }

    // ---- public ----

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(tokens.login(request.login().trim(), request.password()));
    }

    /** Swaps a refresh token for a new pair; the old refresh token stops working. */
    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(tokens.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        tokens.logout(request.refreshToken());
    }

    /** Same rules as the website's sign-up page; the app signs in afterwards. */
    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserInfo signup(@Valid @RequestBody SignupForm form) {
        return UserInfo.from(signups.register(form));
    }

    @GetMapping("/auth/signup-options")
    public SignupOptions signupOptions() {
        return new SignupOptions(classes.findAllByOrderByClassNameAscSectionAsc().stream()
                .map(ApiModels.ClassRef::from).toList(),
                signups.teacherSignupEnabled(), signups.adminSignupEnabled());
    }

    // ---- signed in ----

    @GetMapping("/me")
    public Me me(Principal principal) {
        User user = currentUser(principal);
        String username = user.getUsername();
        return switch (user.getRole()) {
            case STUDENT -> new Me(UserInfo.from(user),
                    students.findByUserUsername(username).map(StudentInfo::from).orElse(null), null);
            case TEACHER -> new Me(UserInfo.from(user), null,
                    teachers.findByUserUsername(username).map(TeacherInfo::from).orElse(null));
            case ADMIN -> new Me(UserInfo.from(user), null, null);
        };
    }

    /** Changes the password, signs out every device, and returns a new session for this one. */
    @PostMapping("/me/password")
    public TokenResponse changePassword(@RequestBody PasswordForm form, Principal principal) {
        User user = currentUser(principal);
        accounts.changeOwnPassword(user, form.getCurrentPassword(), form.getNewPassword(), form.getConfirmPassword());
        return TokenResponse.from(tokens.issue(user));
    }

    private User currentUser(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow();
    }
}
