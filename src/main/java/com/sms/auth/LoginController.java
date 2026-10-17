package com.sms.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import com.sms.admin.StudentAdminService;
import com.sms.admin.StudentForm;
import com.sms.admin.TeacherAdminService;
import com.sms.admin.TeacherForm;
import com.sms.common.FormException;
import com.sms.school.SchoolClassRepository;

@Controller
public class LoginController {

    private final StudentAdminService students;
    private final TeacherAdminService teachers;
    private final AccountService accounts;
    private final SchoolClassRepository classes;
    private final String teacherSignupCode;
    private final String adminSignupCode;

    public LoginController(StudentAdminService students, TeacherAdminService teachers, AccountService accounts,
                           SchoolClassRepository classes,
                           @Value("${sms.teacher.signup-code:}") String teacherSignupCode,
                           @Value("${sms.admin.signup-code:}") String adminSignupCode) {
        this.students = students;
        this.teachers = teachers;
        this.accounts = accounts;
        this.classes = classes;
        this.teacherSignupCode = teacherSignupCode;
        this.adminSignupCode = adminSignupCode;
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
            Role role = parseRole(form.getRole());
            if (role != Role.ADMIN && (form.getName() == null || form.getName().isBlank())) {
                throw new FormException("name", "Enter your full name");
            }
            switch (role) {
                case STUDENT -> registerStudent(form);
                case TEACHER -> registerTeacher(form);
                case ADMIN -> registerAdmin(form);
            }
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
        model.addAttribute("teacherSignupEnabled", teacherSignupCode != null && !teacherSignupCode.isBlank());
        model.addAttribute("adminSignupEnabled", adminSignupCode != null && !adminSignupCode.isBlank());
        return "signup";
    }

    private void registerStudent(SignupForm signup) {
        if (signup.getClassId() == null) throw new FormException("classId", "Select your class and section");
        if (signup.getRollNo() == null || signup.getRollNo().isBlank()) {
            throw new FormException("rollNo", "Enter your roll number");
        }
        StudentForm form = new StudentForm();
        form.setUsername(signup.getUsername());
        form.setEmail(signup.getEmail());
        form.setPassword(signup.getPassword());
        form.setName(signup.getName());
        form.setRollNo(signup.getRollNo());
        form.setClassId(signup.getClassId());
        students.create(form);
    }

    private void registerTeacher(SignupForm signup) {
        checkInvitationCode(signup.getTeacherCode(), teacherSignupCode, "teacherCode",
                "A valid teacher invitation code is required");
        TeacherForm form = new TeacherForm();
        form.setUsername(signup.getUsername());
        form.setEmail(signup.getEmail());
        form.setPassword(signup.getPassword());
        form.setName(signup.getName());
        form.setSubjectSpecialization(signup.getSubjectSpecialization());
        form.setContact(signup.getContact());
        teachers.create(form);
    }

    private void registerAdmin(SignupForm signup) {
        checkInvitationCode(signup.getAdminCode(), adminSignupCode, "adminCode",
                "A valid administrator invitation code is required");
        accounts.create(signup.getUsername().trim(), signup.getEmail().trim(), signup.getPassword(), Role.ADMIN);
    }

    private static void checkInvitationCode(String suppliedCode, String configuredCode, String field, String message) {
        byte[] supplied = suppliedCode == null ? new byte[0] : suppliedCode.getBytes(StandardCharsets.UTF_8);
        byte[] expected = configuredCode == null ? new byte[0] : configuredCode.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, supplied)) {
            throw new FormException(field, message);
        }
    }

    private static Role parseRole(String value) {
        try {
            return Role.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new FormException("role", "Choose a valid account type");
        }
    }

    /** "/" sends a logged-in user to their own dashboard. */
    @GetMapping("/")
    public String home(Authentication authentication) {
        return Role.from(authentication.getAuthorities())
                .map(role -> "redirect:" + role.dashboardUrl())
                .orElse("redirect:/login");
    }
}
