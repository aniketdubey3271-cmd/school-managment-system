package com.sms.school;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    List<Grade> findByStudentSchoolClassAndSubjectAndExamType(SchoolClass schoolClass, Subject subject, ExamType examType);

    Optional<Grade> findByStudentAndSubjectAndExamType(Student student, Subject subject, ExamType examType);

    List<Grade> findByStudentOrderBySubjectNameAsc(Student student);

    List<Grade> findByStudentSchoolClass(SchoolClass schoolClass);
}
