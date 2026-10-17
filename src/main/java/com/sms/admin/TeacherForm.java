package com.sms.admin;

import com.sms.school.Teacher;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeacherForm {

    private Long id;

    @NotBlank
    @Size(max = 50)
    private String username;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    /** Required when creating; blank on edit keeps the current password. */
    private String password;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String subjectSpecialization;

    @Size(max = 20)
    private String contact;

    private boolean enabled = true;

    public static TeacherForm from(Teacher t) {
        TeacherForm f = new TeacherForm();
        f.id = t.getId();
        f.username = t.getUser().getUsername();
        f.email = t.getUser().getEmail();
        f.name = t.getName();
        f.subjectSpecialization = t.getSubjectSpecialization();
        f.contact = t.getContact();
        f.enabled = t.getUser().isEnabled();
        return f;
    }
}
