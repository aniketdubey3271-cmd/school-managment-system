package com.sms.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByTeacherOrderByDueDateDesc(Teacher teacher);

    List<Assignment> findBySchoolClassOrderByDueDateAsc(SchoolClass schoolClass);
}
