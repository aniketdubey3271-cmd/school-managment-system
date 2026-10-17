package com.sms.admin;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.auth.AccountService;
import com.sms.auth.Role;
import com.sms.auth.User;
import com.sms.auth.UserRepository;
import com.sms.common.InUseException;
import com.sms.common.NotFoundException;
import com.sms.school.Teacher;
import com.sms.school.TeacherRepository;

@Service
@Transactional
public class TeacherAdminService {

    private final TeacherRepository teachers;
    private final UserRepository users;
    private final AccountService accounts;

    public TeacherAdminService(TeacherRepository teachers, UserRepository users, AccountService accounts) {
        this.teachers = teachers;
        this.users = users;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<Teacher> findAll() {
        return teachers.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Teacher get(Long id) {
        return teachers.findById(id).orElseThrow(() -> new NotFoundException("Teacher"));
    }

    public Teacher create(TeacherForm form) {
        User user = accounts.create(form.getUsername().trim(), form.getEmail().trim(), form.getPassword(), Role.TEACHER);
        Teacher teacher = new Teacher();
        teacher.setUser(user);
        apply(form, teacher);
        return teachers.save(teacher);
    }

    public void update(Long id, TeacherForm form) {
        Teacher teacher = get(id);
        accounts.update(teacher.getUser(), form.getEmail().trim(), form.isEnabled(), form.getPassword());
        apply(form, teacher);
    }

    /** Throws InUseException if the teacher is still referenced. */
    public void delete(Long id) {
        Teacher teacher = get(id);
        User user = teacher.getUser();
        teachers.delete(teacher);
        users.delete(user);
        InUseException.flushDeletes(teachers);
    }

    private void apply(TeacherForm form, Teacher teacher) {
        teacher.setName(form.getName().trim());
        teacher.setSubjectSpecialization(blankToNull(form.getSubjectSpecialization()));
        teacher.setContact(blankToNull(form.getContact()));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
