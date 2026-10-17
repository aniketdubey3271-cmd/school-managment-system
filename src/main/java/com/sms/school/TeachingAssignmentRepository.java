package com.sms.school;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

    List<TeachingAssignment> findByTeacher(Teacher teacher);

    List<TeachingAssignment> findBySchoolClassOrderBySubjectNameAsc(SchoolClass schoolClass);

    Optional<TeachingAssignment> findBySchoolClassIdAndSubjectId(Long classId, Long subjectId);

    boolean existsBySchoolClassAndSubject(SchoolClass schoolClass, Subject subject);

    boolean existsByTeacherAndSchoolClass(Teacher teacher, SchoolClass schoolClass);
}
