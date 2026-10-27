package com.sms.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sms.admin.StudentAdminService;
import com.sms.admin.StudentForm;
import com.sms.admin.TeacherAdminService;
import com.sms.admin.TeacherForm;
import com.sms.common.FormException;

/**
 * Public registration rules, shared by the website and the mobile API. Students can sign up
 * freely; teachers and administrators need the invitation code configured on the server.
 */
@Service
public class SignupService {

    private final StudentAdminService students;
    private final TeacherAdminService teachers;
    private final AccountService accounts;
    private final String teacherSignupCode;
    private final String adminSignupCode;

    public SignupService(StudentAdminService students, TeacherAdminService teachers, AccountService accounts,
                         @Value("${sms.teacher.signup-code:}") String teacherSignupCode,
                         @Value("${sms.admin.signup-code:}") String adminSignupCode) {
        this.students = students;
        this.teachers = teachers;
        this.accounts = accounts;
        this.teacherSignupCode = teacherSignupCode;
        this.adminSignupCode = adminSignupCode;
    }

    public boolean teacherSignupEnabled() {
        return teacherSignupCode != null && !teacherSignupCode.isBlank();
    }

    public boolean adminSignupEnabled() {
        return adminSignupCode != null && !adminSignupCode.isBlank();
    }

    /** Creates the account; throws FormException with the offending field. */
    public User register(SignupForm form) {
        Role role = parseRole(form.getRole());
        if (role != Role.ADMIN && (form.getName() == null || form.getName().isBlank())) {
            throw new FormException("name", "Enter your full name");
        }
        return switch (role) {
            case STUDENT -> registerStudent(form);
            case TEACHER -> registerTeacher(form);
            case ADMIN -> registerAdmin(form);
        };
    }

    private User registerStudent(SignupForm signup) {
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
        return students.create(form).getUser();
    }

    private User registerTeacher(SignupForm signup) {
        checkInvitationCode(signup.getTeacherCode(), teacherSignupCode, "teacherCode",
                "A valid teacher invitation code is required");
        TeacherForm form = new TeacherForm();
        form.setUsername(signup.getUsername());
        form.setEmail(signup.getEmail());
        form.setPassword(signup.getPassword());
        form.setName(signup.getName());
        form.setSubjectSpecialization(signup.getSubjectSpecialization());
        form.setContact(signup.getContact());
        return teachers.create(form).getUser();
    }

    private User registerAdmin(SignupForm signup) {
        checkInvitationCode(signup.getAdminCode(), adminSignupCode, "adminCode",
                "A valid administrator invitation code is required");
        return accounts.create(signup.getUsername().trim(), signup.getEmail().trim(), signup.getPassword(), Role.ADMIN);
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
}
