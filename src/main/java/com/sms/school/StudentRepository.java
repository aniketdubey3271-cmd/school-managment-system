package com.sms.school;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findAllByOrderByNameAsc();

    Optional<Student> findByUserUsername(String username);

    List<Student> findBySchoolClassOrderByRollNoAsc(SchoolClass schoolClass);

    long countBySchoolClass(SchoolClass schoolClass);

    boolean existsBySchoolClassAndRollNo(SchoolClass schoolClass, String rollNo);

    boolean existsBySchoolClassAndRollNoAndIdNot(SchoolClass schoolClass, String rollNo, Long id);
}
