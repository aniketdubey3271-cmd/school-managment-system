package com.sms;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.sms.admin.ClassAdminService;
import com.sms.admin.ClassForm;
import com.sms.admin.StudentAdminService;
import com.sms.admin.StudentForm;
import com.sms.admin.TeacherAdminService;
import com.sms.admin.TeacherForm;
import com.sms.school.Assignment;
import com.sms.school.AssignmentRepository;
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.Subject;
import com.sms.school.SubjectRepository;
import com.sms.school.Teacher;

/** Builds test data through the real admin services. */
@Component
public class Fixtures {

    public static final String PASSWORD = "password123";

    private final TeacherAdminService teachers;
    private final StudentAdminService students;
    private final ClassAdminService classes;
    private final SubjectRepository subjects;
    private final AssignmentRepository assignments;

    public Fixtures(TeacherAdminService teachers, StudentAdminService students, ClassAdminService classes,
                    SubjectRepository subjects, AssignmentRepository assignments) {
        this.teachers = teachers;
        this.students = students;
        this.classes = classes;
        this.subjects = subjects;
        this.assignments = assignments;
    }

    public static TeacherForm teacherForm(String username) {
        TeacherForm f = new TeacherForm();
        f.setUsername(username);
        f.setEmail(username + "@test.local");
        f.setPassword(PASSWORD);
        f.setName("Teacher " + username);
        return f;
    }

    public Teacher teacher(String username) {
        return teachers.create(teacherForm(username));
    }

    public SchoolClass schoolClass(String name, String section, Teacher classTeacher) {
        ClassForm f = new ClassForm();
        f.setClassName(name);
        f.setSection(section);
        f.setClassTeacherId(classTeacher == null ? null : classTeacher.getId());
        return classes.createClass(f);
    }

    public Subject subject(String name) {
        classes.createSubject(name);
        return subjects.findAllByOrderByNameAsc().stream()
                .filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    public void assign(SchoolClass c, Subject s, Teacher t) {
        classes.assign(c.getId(), s.getId(), t.getId());
    }

    /** Saved directly (not via the teacher service) so tests can use a past due date. */
    public Assignment assignment(Teacher t, SchoolClass c, Subject s, String title, LocalDateTime dueDate) {
        Assignment a = new Assignment();
        a.setTeacher(t);
        a.setSchoolClass(c);
        a.setSubject(s);
        a.setTitle(title);
        a.setDueDate(dueDate);
        return assignments.save(a);
    }

    public Student student(String username, SchoolClass c, String rollNo) {
        StudentForm f = new StudentForm();
        f.setUsername(username);
        f.setEmail(username + "@test.local");
        f.setPassword(PASSWORD);
        f.setName("Student " + username);
        f.setClassId(c.getId());
        f.setRollNo(rollNo);
        return students.create(f);
    }
}
