package com.sms.admin;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.sms.school.Student;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentForm {

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

    @NotBlank
    @Size(max = 20)
    private String rollNo;

    @NotNull(message = "Select a class")
    private Long classId;

    @Past
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dob;

    @Size(max = 255)
    private String address;

    @Size(max = 20)
    private String parentContact;

    private boolean enabled = true;

    public static StudentForm from(Student s) {
        StudentForm f = new StudentForm();
        f.id = s.getId();
        f.username = s.getUser().getUsername();
        f.email = s.getUser().getEmail();
        f.name = s.getName();
        f.rollNo = s.getRollNo();
        f.classId = s.getSchoolClass() == null ? null : s.getSchoolClass().getId();
        f.dob = s.getDob();
        f.address = s.getAddress();
        f.parentContact = s.getParentContact();
        f.enabled = s.getUser().isEnabled();
        return f;
    }
}
