package com.sms.school;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByUserUsername(String username);

    List<Teacher> findAllByOrderByNameAsc();
}
