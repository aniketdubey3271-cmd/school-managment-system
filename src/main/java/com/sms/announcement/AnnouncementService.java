package com.sms.announcement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sms.auth.Role;
import com.sms.auth.User;
import com.sms.common.FormException;
import com.sms.common.NotFoundException;
import com.sms.school.SchoolClass;
import com.sms.school.SchoolClassRepository;
import com.sms.school.Student;
import com.sms.school.Teacher;
import com.sms.school.TeacherRepository;

/**
 * Who sees what: admin sees everything; a teacher sees school-wide notices, notices for
 * classes they teach, and their own posts; a student sees school-wide plus their class.
 */
@Service
@Transactional
public class AnnouncementService {

    /** One announcement with a display name for its author. */
    public record View(Announcement announcement, String author) {
    }

    private final AnnouncementRepository announcements;
    private final SchoolClassRepository classes;
    private final TeacherRepository teachers;

    public AnnouncementService(AnnouncementRepository announcements, SchoolClassRepository classes,
                               TeacherRepository teachers) {
        this.announcements = announcements;
        this.classes = classes;
        this.teachers = teachers;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<View> forAdmin() {
        return withAuthors(announcements.findAllByOrderByCreatedAtDescIdDesc());
    }

    @Transactional(readOnly = true)
    public List<View> forTeacher(Teacher teacher, Set<Long> classIds) {
        // An empty IN list is not portable SQL; -1 matches no class.
        Set<Long> ids = classIds.isEmpty() ? Set.of(-1L) : classIds;
        return withAuthors(announcements.findForTeacher(ids, teacher.getUser()));
    }

    /** limit null = all. */
    @Transactional(readOnly = true)
    public List<View> forStudent(Student student, Integer limit) {
        Limit l = limit == null ? Limit.unlimited() : Limit.of(limit);
        return withAuthors(student.getSchoolClass() == null
                ? announcements.findByTargetClassIsNullOrderByCreatedAtDescIdDesc(l)
                : announcements.findForClass(student.getSchoolClass(), l));
    }

    // ---- posting ----

    /** Admin: targetClassId null = school-wide. */
    public Announcement postAsAdmin(User admin, AnnouncementForm form) {
        SchoolClass target = form.getTargetClassId() == null ? null : findClass(form.getTargetClassId());
        return save(admin, form, target);
    }

    /** Teacher: must target one of their own classes. */
    public Announcement postAsTeacher(Teacher teacher, Set<Long> allowedClassIds, AnnouncementForm form) {
        if (form.getTargetClassId() == null || !allowedClassIds.contains(form.getTargetClassId())) {
            throw new FormException("targetClassId", "Choose one of your classes");
        }
        return save(teacher.getUser(), form, findClass(form.getTargetClassId()));
    }

    // ---- deleting ----

    public void deleteAsAdmin(Long id) {
        announcements.delete(get(id));
    }

    public void deleteOwn(User user, Long id) {
        Announcement a = get(id);
        if (!a.getPostedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your announcement");
        }
        announcements.delete(a);
    }

    private Announcement save(User author, AnnouncementForm form, SchoolClass target) {
        Announcement a = new Announcement();
        a.setPostedBy(author);
        a.setTitle(form.getTitle().trim());
        a.setContent(form.getContent().trim());
        a.setTargetClass(target);
        a.setCreatedAt(LocalDateTime.now());
        return announcements.save(a);
    }

    private Announcement get(Long id) {
        return announcements.findById(id).orElseThrow(() -> new NotFoundException("Announcement"));
    }

    private SchoolClass findClass(Long id) {
        return classes.findById(id).orElseThrow(() -> new FormException("targetClassId", "Class not found"));
    }

    private List<View> withAuthors(List<Announcement> list) {
        var teacherNames = teachers.findAll().stream()
                .collect(Collectors.toMap(t -> t.getUser().getId(), Teacher::getName));
        return list.stream()
                .map(a -> new View(a, a.getPostedBy().getRole() == Role.ADMIN ? "School office"
                        : teacherNames.getOrDefault(a.getPostedBy().getId(), a.getPostedBy().getUsername())))
                .toList();
    }
}
