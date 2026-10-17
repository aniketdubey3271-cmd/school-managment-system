package com.sms.school;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByStudentSchoolClassAndDate(SchoolClass schoolClass, LocalDate date);

    Optional<Attendance> findByStudentAndDate(Student student, LocalDate date);

    List<Attendance> findByStudentOrderByDateDesc(Student student);

    List<Attendance> findByStudentSchoolClassAndDateBetween(SchoolClass schoolClass, LocalDate from, LocalDate to);

    List<Attendance> findByDateBetween(LocalDate from, LocalDate to);
}
