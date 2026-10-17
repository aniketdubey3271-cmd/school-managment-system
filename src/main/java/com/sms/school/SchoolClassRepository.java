package com.sms.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findAllByOrderByClassNameAscSectionAsc();

    List<SchoolClass> findByClassTeacher(Teacher classTeacher);

    boolean existsByClassNameIgnoreCaseAndSectionIgnoreCase(String className, String section);

    boolean existsByClassNameIgnoreCaseAndSectionIgnoreCaseAndIdNot(String className, String section, Long id);
}
