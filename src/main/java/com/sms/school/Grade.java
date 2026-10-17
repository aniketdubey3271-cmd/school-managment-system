package com.sms.school;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One student's marks in one subject for one exam. */
@Entity
@Table(name = "grades")
@Getter
@Setter
@NoArgsConstructor
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 50)
    private ExamType examType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal marks;

    @Column(name = "max_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxMarks;

    @ManyToOne
    @JoinColumn(name = "entered_by")
    private Teacher enteredBy;

    /** Marks as a whole-number percentage of max marks. */
    public int getPercent() {
        return marks.multiply(BigDecimal.valueOf(100)).divide(maxMarks, 0, RoundingMode.HALF_UP).intValue();
    }

    /** "42.5 / 50" without trailing zeros. */
    public String getScoreText() {
        return marks.stripTrailingZeros().toPlainString() + " / " + maxMarks.stripTrailingZeros().toPlainString();
    }
}
