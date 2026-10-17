package com.sms.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sms.Fixtures;
import com.sms.school.Attendance;
import com.sms.school.AttendanceRepository;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.GradeRepository;
import com.sms.school.Student;
import com.sms.school.StudentRepository;
import com.sms.school.Subject;

/** "stu1" and "stu2" share class 6-A; each test checks stu1 only ever sees stu1's data. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
        "sms.uploads.dir=${java.io.tmpdir}/sms-test-uploads"})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "stu1", roles = "STUDENT")
class StudentPortalTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired AttendanceRepository attendance;
    @Autowired GradeRepository grades;
    @Autowired StudentRepository students;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        var teacher = fixtures.teacher("sp_teacher");
        var c = fixtures.schoolClass("6", "A", teacher);
        Subject maths = fixtures.subject("SpMaths");
        Student stu1 = fixtures.student("stu1", c, "1");
        Student stu2 = fixtures.student("stu2", c, "2");

        LocalDate today = LocalDate.now();
        mark(stu1, today.minusDays(3), AttendanceStatus.PRESENT);
        mark(stu1, today.minusDays(2), AttendanceStatus.PRESENT);
        mark(stu1, today.minusDays(1), AttendanceStatus.ABSENT);
        mark(stu2, today.minusDays(1), AttendanceStatus.ABSENT);
        mark(stu2, today.minusDays(2), AttendanceStatus.ABSENT);

        grade(stu1, maths, "42.5");
        grade(stu2, maths, "10");
    }

    @Test
    void everyStudentPageRenders() throws Exception {
        for (String url : new String[] {"/student/dashboard", "/student/profile", "/student/attendance",
                "/student/grades", "/student/assignments"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void attendanceShowsOnlyOwnRecords() throws Exception {
        mvc.perform(get("/student/attendance"))
                .andExpect(model().attribute("summary", new StudentPortalService.AttendanceSummary(2, 1, 67)))
                .andExpect(model().attribute("records", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void gradesShowOnlyOwnMarks() throws Exception {
        mvc.perform(get("/student/grades"))
                .andExpect(content().string(containsString("42.5 / 50")))
                .andExpect(content().string(containsString("85%")))
                .andExpect(content().string(not(containsString("10 / 50"))));
    }

    @Test
    void passwordChangeChecksCurrentAndConfirmation() throws Exception {
        mvc.perform(post("/student/profile/password").with(csrf())
                        .param("currentPassword", "wrong-one").param("newPassword", "brand-new-pw")
                        .param("confirmPassword", "brand-new-pw"))
                .andExpect(model().attributeHasFieldErrors("form", "currentPassword"));

        mvc.perform(post("/student/profile/password").with(csrf())
                        .param("currentPassword", Fixtures.PASSWORD).param("newPassword", "brand-new-pw")
                        .param("confirmPassword", "different-pw"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));

        mvc.perform(post("/student/profile/password").with(csrf())
                        .param("currentPassword", Fixtures.PASSWORD).param("newPassword", "brand-new-pw")
                        .param("confirmPassword", "brand-new-pw"))
                .andExpect(redirectedUrl("/student/profile"));

        String hash = students.findByUserUsername("stu1").orElseThrow().getUser().getPassword();
        assertThat(passwordEncoder.matches("brand-new-pw", hash)).isTrue();
    }

    @Test
    void studentCannotOpenTeacherOrAdminPages() throws Exception {
        mvc.perform(get("/teacher/classes")).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/assignments")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/students")).andExpect(status().isForbidden());
    }

    private void mark(Student s, LocalDate date, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.setStudent(s);
        a.setDate(date);
        a.setStatus(status);
        attendance.save(a);
    }

    private void grade(Student s, Subject subject, String marks) {
        Grade g = new Grade();
        g.setStudent(s);
        g.setSubject(subject);
        g.setExamType(ExamType.MIDTERM);
        g.setMarks(new BigDecimal(marks));
        g.setMaxMarks(new BigDecimal("50"));
        grades.save(g);
    }
}
