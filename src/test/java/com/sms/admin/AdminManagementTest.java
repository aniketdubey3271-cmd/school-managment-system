package com.sms.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sms.Fixtures;
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.StudentRepository;
import com.sms.school.Subject;
import com.sms.school.Teacher;
import com.sms.school.TeacherRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on")
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class AdminManagementTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired TeacherRepository teachers;
    @Autowired StudentRepository students;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void everyAdminPageRenders() throws Exception {
        Teacher t = fixtures.teacher("render_t");
        SchoolClass c = fixtures.schoolClass("9", "R", t);
        Subject s = fixtures.subject("RenderSubject");
        fixtures.assign(c, s, t);
        Student st = fixtures.student("render_s", c, "1");

        for (String url : new String[] {
                "/admin/dashboard",
                "/admin/teachers", "/admin/teachers/new", "/admin/teachers/" + t.getId() + "/edit",
                "/admin/students", "/admin/students?classId=" + c.getId(), "/admin/students/new",
                "/admin/students/" + st.getId() + "/edit",
                "/admin/classes", "/admin/classes/new", "/admin/classes/" + c.getId(),
                "/admin/classes/" + c.getId() + "/edit",
                "/admin/subjects"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void createTeacherCreatesLoginWithHashedPassword() throws Exception {
        mvc.perform(post("/admin/teachers").with(csrf())
                        .param("username", "new_t").param("email", "new_t@test.local")
                        .param("password", "secret-pass").param("name", "New Teacher"))
                .andExpect(redirectedUrl("/admin/teachers"));

        Teacher t = teachers.findByUserUsername("new_t").orElseThrow();
        assertThat(t.getName()).isEqualTo("New Teacher");
        assertThat(t.getUser().getRole().name()).isEqualTo("TEACHER");
        assertThat(passwordEncoder.matches("secret-pass", t.getUser().getPassword())).isTrue();
    }

    @Test
    void duplicateUsernameAndShortPasswordAreFieldErrors() throws Exception {
        fixtures.teacher("dup_t");

        mvc.perform(post("/admin/teachers").with(csrf())
                        .param("username", "dup_t").param("email", "other@test.local")
                        .param("password", "secret-pass").param("name", "X"))
                .andExpect(view().name("admin/teachers/form"))
                .andExpect(model().attributeHasFieldErrors("form", "username"));

        mvc.perform(post("/admin/teachers").with(csrf())
                        .param("username", "short_pw").param("email", "short@test.local")
                        .param("password", "123").param("name", "X"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));
    }

    @Test
    void rollNumberMustBeUniqueWithinClass() throws Exception {
        SchoolClass c = fixtures.schoolClass("9", "U", null);
        fixtures.student("roll_a", c, "7");

        mvc.perform(post("/admin/students").with(csrf())
                        .param("username", "roll_b").param("email", "roll_b@test.local")
                        .param("password", "secret-pass").param("name", "B")
                        .param("classId", c.getId().toString()).param("rollNo", "7"))
                .andExpect(model().attributeHasFieldErrors("form", "rollNo"));
    }

    @Test
    void studentDateOfBirthRoundTripsThroughSqlite() throws Exception {
        SchoolClass c = fixtures.schoolClass("9", "D", null);

        mvc.perform(post("/admin/students").with(csrf())
                        .param("username", "dob_s").param("email", "dob_s@test.local")
                        .param("password", "secret-pass").param("name", "Dob Student")
                        .param("classId", c.getId().toString()).param("rollNo", "1")
                        .param("dob", "2012-03-04"))
                .andExpect(redirectedUrl("/admin/students"));

        Student s = students.findAllByOrderByNameAsc().stream()
                .filter(x -> x.getUser().getUsername().equals("dob_s")).findFirst().orElseThrow();
        assertThat(s.getDob()).isEqualTo(LocalDate.of(2012, 3, 4));
    }

    @Test
    void assigningASubjectTwiceInOneClassIsRefused() throws Exception {
        Teacher t = fixtures.teacher("assign_t");
        SchoolClass c = fixtures.schoolClass("9", "S", null);
        Subject s = fixtures.subject("AssignSubject");
        fixtures.assign(c, s, t);

        mvc.perform(post("/admin/classes/" + c.getId() + "/assignments").with(csrf())
                        .param("subjectId", s.getId().toString()).param("teacherId", t.getId().toString()))
                .andExpect(flash().attributeExists("error"));
    }

    /** Runs outside the test transaction: the failed delete must roll back for real. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingATeacherStillInUseIsRefusedAndKeepsData() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Teacher t = fixtures.teacher("inuse_" + suffix);
        fixtures.schoolClass("9", "X" + suffix, t);

        mvc.perform(post("/admin/teachers/" + t.getId() + "/delete").with(csrf()))
                .andExpect(redirectedUrl("/admin/teachers"))
                .andExpect(flash().attributeExists("error"));

        assertThat(teachers.findById(t.getId())).isPresent();
    }
}
