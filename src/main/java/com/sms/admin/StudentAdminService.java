package com.sms.admin;

import static com.sms.admin.TeacherAdminService.blankToNull;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.auth.AccountService;
import com.sms.auth.Role;
import com.sms.auth.User;
import com.sms.auth.UserRepository;
import com.sms.common.FormException;
import com.sms.common.InUseException;
import com.sms.common.NotFoundException;
import com.sms.school.SchoolClass;
import com.sms.school.SchoolClassRepository;
import com.sms.school.Student;
import com.sms.school.StudentRepository;

@Service
@Transactional
public class StudentAdminService {

    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final UserRepository users;
    private final AccountService accounts;

    public StudentAdminService(StudentRepository students, SchoolClassRepository classes,
                               UserRepository users, AccountService accounts) {
        this.students = students;
        this.classes = classes;
        this.users = users;
        this.accounts = accounts;
    }

    /** classId null = all students. */
    @Transactional(readOnly = true)
    public List<Student> find(Long classId) {
        if (classId == null) {
            return students.findAllByOrderByNameAsc();
        }
        return students.findBySchoolClassOrderByRollNoAsc(getClass(classId));
    }

    @Transactional(readOnly = true)
    public Student get(Long id) {
        return students.findById(id).orElseThrow(() -> new NotFoundException("Student"));
    }

    public Student create(StudentForm form) {
        SchoolClass schoolClass = getClass(form.getClassId());
        String rollNo = form.getRollNo().trim();
        if (students.existsBySchoolClassAndRollNo(schoolClass, rollNo)) {
            throw rollNoTaken(rollNo, schoolClass);
        }
        User user = accounts.create(form.getUsername().trim(), form.getEmail().trim(), form.getPassword(), Role.STUDENT);
        Student student = new Student();
        student.setUser(user);
        apply(form, student, schoolClass, rollNo);
        return students.save(student);
    }

    public void update(Long id, StudentForm form) {
        Student student = get(id);
        SchoolClass schoolClass = getClass(form.getClassId());
        String rollNo = form.getRollNo().trim();
        if (students.existsBySchoolClassAndRollNoAndIdNot(schoolClass, rollNo, id)) {
            throw rollNoTaken(rollNo, schoolClass);
        }
        accounts.update(student.getUser(), form.getEmail().trim(), form.isEnabled(), form.getPassword());
        apply(form, student, schoolClass, rollNo);
    }

    /** Throws InUseException if the student has attendance/grades/submissions. */
    public void delete(Long id) {
        Student student = get(id);
        User user = student.getUser();
        students.delete(student);
        users.delete(user);
        InUseException.flushDeletes(students);
    }

    private SchoolClass getClass(Long classId) {
        return classes.findById(classId).orElseThrow(() -> new FormException("classId", "Class not found"));
    }

    private static FormException rollNoTaken(String rollNo, SchoolClass schoolClass) {
        return new FormException("rollNo", "Roll number " + rollNo + " is already used in " + schoolClass.getDisplayName());
    }

    private void apply(StudentForm form, Student student, SchoolClass schoolClass, String rollNo) {
        student.setName(form.getName().trim());
        student.setRollNo(rollNo);
        student.setSchoolClass(schoolClass);
        student.setDob(form.getDob());
        student.setAddress(blankToNull(form.getAddress()));
        student.setParentContact(blankToNull(form.getParentContact()));
    }
}
