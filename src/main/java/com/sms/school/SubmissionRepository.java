package com.sms.school;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByAssignment(Assignment assignment);

    List<Submission> findByStudent(Student student);

    Optional<Submission> findByAssignmentAndStudent(Assignment assignment, Student student);

    long countByAssignment(Assignment assignment);
}
