package com.sms.admin;

import com.sms.school.SchoolClass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClassForm {

    private Long id;

    @NotBlank
    @Size(max = 50)
    private String className;

    @NotBlank
    @Size(max = 10)
    private String section;

    /** Optional. */
    private Long classTeacherId;

    public static ClassForm from(SchoolClass c) {
        ClassForm f = new ClassForm();
        f.id = c.getId();
        f.className = c.getClassName();
        f.section = c.getSection();
        f.classTeacherId = c.getClassTeacher() == null ? null : c.getClassTeacher().getId();
        return f;
    }
}
