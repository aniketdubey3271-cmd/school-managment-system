package com.sms;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.sms.admin.ClassAdminService;
import com.sms.admin.ClassForm;
import com.sms.admin.StudentAdminService;
import com.sms.admin.StudentForm;
import com.sms.admin.TeacherAdminService;
import com.sms.admin.TeacherForm;
import com.sms.auth.Role;
import com.sms.auth.UserRepository;
import com.sms.school.SchoolClass;
import com.sms.school.SchoolClassRepository;
import com.sms.school.StudentRepository;
import com.sms.school.Subject;
import com.sms.school.Teacher;
import com.sms.school.TeacherRepository;
import com.sms.school.TeachingAssignment;

/** Adds local demo records up to the configured role counts without replacing existing data. */
@Component
@Order(2)
@ConditionalOnProperty(name = "sms.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final long TARGET_STUDENTS = 21;
    private static final long TARGET_TEACHERS = 3;
    private static final String CLASS_NAME = "BCA";
    private static final String CLASS_SECTION = "SEM-5";
    private static final List<String> SUBJECT_NAMES = List.of(
            "Data Structures", "Database Systems", "Software Engineering");

    private final UserRepository users;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;
    private final TeacherAdminService teacherAdminService;
    private final StudentAdminService studentAdminService;
    private final ClassAdminService classAdminService;
    private final String demoPassword;

    public DemoDataSeeder(UserRepository users, StudentRepository students, TeacherRepository teachers,
                          SchoolClassRepository classes, TeacherAdminService teacherAdminService,
                          StudentAdminService studentAdminService, ClassAdminService classAdminService,
                          @Value("${sms.demo-data.password}") String demoPassword) {
        this.users = users;
        this.students = students;
        this.teachers = teachers;
        this.classes = classes;
        this.teacherAdminService = teacherAdminService;
        this.studentAdminService = studentAdminService;
        this.classAdminService = classAdminService;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByRole(Role.ADMIN)) {
            log.warn("Demo records were not created because no admin exists; set SMS_ADMIN_PASSWORD first.");
            return;
        }

        SchoolClass demoClass = getOrCreateDemoClass();
        seedTeachers();
        seedStudents(demoClass);
        seedSubjectsAndAssignments(demoClass);

        log.info("Demo data ready: {} students, {} teachers, and {} admin(s).",
                students.count(), teachers.count(), users.countByRole(Role.ADMIN));
    }

    private SchoolClass getOrCreateDemoClass() {
        return classes.findAllByOrderByClassNameAscSectionAsc().stream()
                .filter(schoolClass -> schoolClass.getClassName().equalsIgnoreCase(CLASS_NAME)
                        && schoolClass.getSection().equalsIgnoreCase(CLASS_SECTION))
                .findFirst()
                .orElseGet(() -> {
                    ClassForm form = new ClassForm();
                    form.setClassName(CLASS_NAME);
                    form.setSection(CLASS_SECTION);
                    return classAdminService.createClass(form);
                });
    }

    private void seedTeachers() {
        int sequence = 1;
        while (teachers.count() < TARGET_TEACHERS) {
            String suffix = String.format(Locale.ROOT, "%02d", sequence++);
            String username = "demo-teacher-" + suffix;
            String email = "teacher" + suffix + "@example.test";
            if (users.existsByUsername(username) || users.existsByEmail(email)) {
                continue;
            }

            TeacherForm form = new TeacherForm();
            form.setUsername(username);
            form.setEmail(email);
            form.setPassword(demoPassword);
            form.setName("Sample Teacher " + suffix);
            teacherAdminService.create(form);
        }
    }

    private void seedStudents(SchoolClass schoolClass) {
        int sequence = 1;
        while (students.count() < TARGET_STUDENTS) {
            String suffix = String.format(Locale.ROOT, "%02d", sequence++);
            String username = "demo-student-" + suffix;
            String email = "student" + suffix + "@example.test";
            if (users.existsByUsername(username) || users.existsByEmail(email)) {
                continue;
            }

            StudentForm form = new StudentForm();
            form.setUsername(username);
            form.setEmail(email);
            form.setPassword(demoPassword);
            form.setName("Sample Student " + suffix);
            form.setRollNo("D" + suffix);
            form.setClassId(schoolClass.getId());
            studentAdminService.create(form);
        }
    }

    private void seedSubjectsAndAssignments(SchoolClass schoolClass) {
        for (String subjectName : SUBJECT_NAMES) {
            boolean exists = classAdminService.findAllSubjects().stream()
                    .anyMatch(subject -> subject.getName().equalsIgnoreCase(subjectName));
            if (!exists) {
                classAdminService.createSubject(subjectName);
            }
        }

        List<Teacher> teacherList = teachers.findAllByOrderByNameAsc();
        List<Subject> subjectList = classAdminService.findAllSubjects().stream()
                .filter(subject -> SUBJECT_NAMES.stream()
                        .anyMatch(name -> name.equalsIgnoreCase(subject.getName())))
                .toList();
        List<TeachingAssignment> existingAssignments = classAdminService.assignmentsFor(schoolClass);
        for (int i = 0; i < Math.min(teacherList.size(), subjectList.size()); i++) {
            Subject subject = subjectList.get(i);
            boolean alreadyAssigned = existingAssignments.stream()
                    .anyMatch(assignment -> assignment.getSubject().getId().equals(subject.getId()));
            if (!alreadyAssigned) {
                classAdminService.assign(schoolClass.getId(), subject.getId(), teacherList.get(i).getId());
            }
        }
    }
}