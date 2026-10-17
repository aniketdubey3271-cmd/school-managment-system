package com.sms.admin;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.common.FormException;
import com.sms.common.InUseException;
import com.sms.common.NotFoundException;
import com.sms.school.SchoolClass;
import com.sms.school.SchoolClassRepository;
import com.sms.school.StudentRepository;
import com.sms.school.Subject;
import com.sms.school.SubjectRepository;
import com.sms.school.Teacher;
import com.sms.school.TeacherRepository;
import com.sms.school.TeachingAssignment;
import com.sms.school.TeachingAssignmentRepository;

/** Classes, subjects, and who teaches what where. */
@Service
@Transactional
public class ClassAdminService {

    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final TeacherRepository teachers;
    private final TeachingAssignmentRepository assignments;
    private final StudentRepository students;

    public ClassAdminService(SchoolClassRepository classes, SubjectRepository subjects, TeacherRepository teachers,
                             TeachingAssignmentRepository assignments, StudentRepository students) {
        this.classes = classes;
        this.subjects = subjects;
        this.teachers = teachers;
        this.assignments = assignments;
        this.students = students;
    }

    // ---- classes ----

    @Transactional(readOnly = true)
    public List<SchoolClass> findAllClasses() {
        return classes.findAllByOrderByClassNameAscSectionAsc();
    }

    @Transactional(readOnly = true)
    public SchoolClass getClass(Long id) {
        return classes.findById(id).orElseThrow(() -> new NotFoundException("Class"));
    }

    @Transactional(readOnly = true)
    public long countStudents(SchoolClass schoolClass) {
        return students.countBySchoolClass(schoolClass);
    }

    public SchoolClass createClass(ClassForm form) {
        String name = form.getClassName().trim();
        String section = form.getSection().trim();
        if (classes.existsByClassNameIgnoreCaseAndSectionIgnoreCase(name, section)) {
            throw classExists(name, section);
        }
        SchoolClass schoolClass = new SchoolClass();
        apply(form, schoolClass, name, section);
        return classes.save(schoolClass);
    }

    public void updateClass(Long id, ClassForm form) {
        SchoolClass schoolClass = getClass(id);
        String name = form.getClassName().trim();
        String section = form.getSection().trim();
        if (classes.existsByClassNameIgnoreCaseAndSectionIgnoreCaseAndIdNot(name, section, id)) {
            throw classExists(name, section);
        }
        apply(form, schoolClass, name, section);
    }

    /** Throws InUseException if students/assignments still use the class. */
    public void deleteClass(Long id) {
        classes.delete(getClass(id));
        InUseException.flushDeletes(classes);
    }

    private static FormException classExists(String name, String section) {
        return new FormException("section", "Class " + name + "-" + section + " already exists");
    }

    private void apply(ClassForm form, SchoolClass schoolClass, String name, String section) {
        schoolClass.setClassName(name);
        schoolClass.setSection(section);
        schoolClass.setClassTeacher(form.getClassTeacherId() == null ? null
                : teachers.findById(form.getClassTeacherId())
                        .orElseThrow(() -> new FormException("classTeacherId", "Teacher not found")));
    }

    // ---- subjects ----

    @Transactional(readOnly = true)
    public List<Subject> findAllSubjects() {
        return subjects.findAllByOrderByNameAsc();
    }

    public void createSubject(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new FormException("Subject name must be 1 to 100 characters");
        }
        if (subjects.existsByNameIgnoreCase(trimmed)) {
            throw new FormException("Subject " + trimmed + " already exists");
        }
        subjects.save(new Subject(trimmed));
    }

    /** Throws InUseException if the subject is assigned or graded. */
    public void deleteSubject(Long id) {
        subjects.delete(subjects.findById(id).orElseThrow(() -> new NotFoundException("Subject")));
        InUseException.flushDeletes(subjects);
    }

    // ---- teaching assignments ----

    @Transactional(readOnly = true)
    public List<TeachingAssignment> assignmentsFor(SchoolClass schoolClass) {
        return assignments.findBySchoolClassOrderBySubjectNameAsc(schoolClass);
    }

    public void assign(Long classId, Long subjectId, Long teacherId) {
        SchoolClass schoolClass = getClass(classId);
        Subject subject = subjectId == null ? null : subjects.findById(subjectId).orElse(null);
        Teacher teacher = teacherId == null ? null : teachers.findById(teacherId).orElse(null);
        if (subject == null || teacher == null) {
            throw new FormException("Select both a subject and a teacher");
        }
        if (assignments.existsBySchoolClassAndSubject(schoolClass, subject)) {
            throw new FormException(subject.getName() + " already has a teacher in " + schoolClass.getDisplayName()
                    + "; remove that assignment first");
        }
        assignments.save(new TeachingAssignment(schoolClass, subject, teacher));
    }

    public void unassign(Long classId, Long assignmentId) {
        TeachingAssignment assignment = assignments.findById(assignmentId)
                .filter(a -> a.getSchoolClass().getId().equals(classId))
                .orElseThrow(() -> new NotFoundException("Assignment"));
        assignments.delete(assignment);
    }
}
