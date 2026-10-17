package com.sms.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Public registration fields; the server decides which role-specific values are required. */
@Getter
@Setter
public class SignupForm {

    @NotBlank
    @Size(max = 50)
    private String username;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    private String role = "STUDENT";

    @Size(max = 100)
    private String name;

    @Size(max = 20)
    private String rollNo;

    private Long classId;

    @Size(max = 100)
    private String subjectSpecialization;

    @Size(max = 20)
    private String contact;

    @Size(max = 128)
    private String teacherCode;

    @Size(max = 128)
    private String adminCode;
}
