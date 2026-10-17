package com.sms.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.sms.Fixtures;
import com.sms.auth.AccountService;
import com.sms.auth.Role;
import com.sms.school.SchoolClass;

/**
 * Classes 4-A (taught by "ann_t_a", student "ann_s_a") and 4-B (taught by "ann_t_b", student "ann_s_b").
 * Posts are made through the real endpoints; each test's data is rolled back.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
        "sms.demo-data.enabled=false",
        "sms.uploads.dir=${java.io.tmpdir}/sms-test-uploads"})
@AutoConfigureMockMvc
@Transactional
class AnnouncementTest {

    private static final RequestPostProcessor ADMIN = user("ann_admin").roles("ADMIN");
    private static final RequestPostProcessor TEACHER_A = user("ann_t_a").roles("TEACHER");
    private static final RequestPostProcessor TEACHER_B = user("ann_t_b").roles("TEACHER");
    private static final RequestPostProcessor STUDENT_A = user("ann_s_a").roles("STUDENT");

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired AccountService accounts;
    @Autowired AnnouncementRepository announcements;

    SchoolClass classA;
    SchoolClass classB;

    @BeforeEach
    void setUp() throws Exception {
        accounts.create("ann_admin", "ann_admin@test.local", Fixtures.PASSWORD, Role.ADMIN);
        classA = fixtures.schoolClass("4", "A", fixtures.teacher("ann_t_a"));
        classB = fixtures.schoolClass("4", "B", fixtures.teacher("ann_t_b"));
        fixtures.student("ann_s_a", classA, "1");
        fixtures.student("ann_s_b", classB, "1");

        postAs(ADMIN, "/admin/announcements", "School closed Friday", null);
        postAs(ADMIN, "/admin/announcements", "Class B trip", classB.getId());
        postAs(TEACHER_A, "/teacher/announcements", "Class A test Monday", classA.getId());
        postAs(TEACHER_B, "/teacher/announcements", "Class B homework", classB.getId());
    }

    @Test
    void everyAnnouncementPageRenders() throws Exception {
        mvc.perform(get("/admin/announcements").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/admin/dashboard").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/teacher/announcements").with(TEACHER_A)).andExpect(status().isOk());
        mvc.perform(get("/teacher/dashboard").with(TEACHER_A)).andExpect(status().isOk());
        mvc.perform(get("/student/announcements").with(STUDENT_A)).andExpect(status().isOk());
        mvc.perform(get("/student/dashboard").with(STUDENT_A)).andExpect(status().isOk());
    }

    @Test
    void studentSeesSchoolWideAndOwnClassOnly() throws Exception {
        mvc.perform(get("/student/announcements").with(STUDENT_A))
                .andExpect(content().string(containsString("School closed Friday")))
                .andExpect(content().string(containsString("Class A test Monday")))
                .andExpect(content().string(not(containsString("Class B trip"))))
                .andExpect(content().string(not(containsString("Class B homework"))));
        mvc.perform(get("/student/dashboard").with(STUDENT_A))
                .andExpect(content().string(containsString("Class A test Monday")));
    }

    @Test
    void teacherSeesSchoolWideOwnClassesAndOwnPostsOnly() throws Exception {
        mvc.perform(get("/teacher/announcements").with(TEACHER_A))
                .andExpect(content().string(containsString("School closed Friday")))
                .andExpect(content().string(containsString("Class A test Monday")))
                .andExpect(content().string(not(containsString("Class B trip"))))
                .andExpect(content().string(not(containsString("Class B homework"))));
    }

    @Test
    void teacherCanOnlyPostToOwnClasses() throws Exception {
        long before = announcements.count();
        mvc.perform(post("/teacher/announcements").with(TEACHER_A).with(csrf())
                        .param("title", "Sneaky").param("content", "x").param("targetClassId", classB.getId().toString()))
                .andExpect(model().attributeHasFieldErrors("form", "targetClassId"));
        mvc.perform(post("/teacher/announcements").with(TEACHER_A).with(csrf())
                        .param("title", "Everyone!").param("content", "x"))
                .andExpect(model().attributeHasFieldErrors("form", "targetClassId"));
        assertThat(announcements.count()).isEqualTo(before);
    }

    @Test
    void teacherDeletesOnlyOwnPostsAdminDeletesAny() throws Exception {
        Long adminPost = idOf("School closed Friday");
        Long teacherBPost = idOf("Class B homework");
        Long teacherAPost = idOf("Class A test Monday");

        mvc.perform(post("/teacher/announcements/" + adminPost + "/delete").with(TEACHER_A).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/teacher/announcements/" + teacherBPost + "/delete").with(TEACHER_A).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/teacher/announcements/" + teacherAPost + "/delete").with(TEACHER_A).with(csrf()))
                .andExpect(redirectedUrl("/teacher/announcements"));
        mvc.perform(post("/admin/announcements/" + teacherBPost + "/delete").with(ADMIN).with(csrf()))
                .andExpect(redirectedUrl("/admin/announcements"));

        assertThat(announcements.findById(adminPost)).isPresent();
        assertThat(announcements.findById(teacherAPost)).isEmpty();
        assertThat(announcements.findById(teacherBPost)).isEmpty();
    }

    @Test
    void studentCannotPostOrDelete() throws Exception {
        mvc.perform(post("/admin/announcements").with(STUDENT_A).with(csrf())
                        .param("title", "x").param("content", "x"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/teacher/announcements").with(STUDENT_A).with(csrf())
                        .param("title", "x").param("content", "x").param("targetClassId", classA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void contentIsEscaped() throws Exception {
        postAs(ADMIN, "/admin/announcements", "<script>alert(1)</script>", null);
        mvc.perform(get("/student/announcements").with(STUDENT_A))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    private void postAs(RequestPostProcessor who, String url, String title, Long classId) throws Exception {
        var request = post(url).with(who).with(csrf()).param("title", title).param("content", "Details for " + title);
        if (classId != null) {
            request.param("targetClassId", classId.toString());
        }
        mvc.perform(request).andExpect(redirectedUrl(url));
    }

    private Long idOf(String title) {
        return announcements.findAll().stream().filter(a -> a.getTitle().equals(title)).findFirst().orElseThrow().getId();
    }
}
