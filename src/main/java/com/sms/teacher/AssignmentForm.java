package com.sms.teacher;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.sms.school.Assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentForm {

    private Long id;

    /** Which of the teacher's class+subject pairs; required on create, ignored on edit. */
    private Long teachingId;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 5000)
    private String description;

    @NotNull(message = "Set a due date")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dueDate;

    public static AssignmentForm from(Assignment a) {
        AssignmentForm f = new AssignmentForm();
        f.id = a.getId();
        f.title = a.getTitle();
        f.description = a.getDescription();
        f.dueDate = a.getDueDate();
        return f;
    }
}
