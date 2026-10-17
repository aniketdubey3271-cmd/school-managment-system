package com.sms.teacher;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.sms.school.ExamType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GradeForm {

    private ExamType examType;

    private BigDecimal maxMarks;

    /** studentId -> marks; a blank entry leaves that student's grade unchanged. */
    private Map<Long, BigDecimal> marks = new HashMap<>();
}
