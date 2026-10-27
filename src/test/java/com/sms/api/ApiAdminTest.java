package com.sms.api;

import static com.sms.api.ApiTestSupport.accessToken;
import static com.sms.api.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.sms.Fixtures;
import com.sms.auth.AccountService;
import com.sms.auth.Role;
import com.sms.school.SchoolClass;
import com.sms.school.SchoolClassRepository;
import com.sms.school.Student;
import com.sms.school.StudentRepository;
import com.sms.school.Subject;
import com.sms.school.Teacher;
import com.sms.school.TeacherRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
        "sms.demo-data.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ApiAdminTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired AccountService accounts;
    @Autowired StudentRepository studentRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired SchoolClassRepository classRepository;

    SchoolClass tenA;
    Student student;
    String admin;

    @BeforeEach
    void setUp() throws Exception {
        accounts.create("api_admin", "api_admin@test.local", Fixtures.PASSWORD, Role.ADMIN);
        Teacher teacher = fixtures.teacher("api_adm_teacher");
        tenA = fixtures.schoolClass("10", "A", teacher);
        SchoolClass tenB = fixtures.schoolClass("10", "B", null);
        Subject english = fixtures.subject("ApiEnglish");
        fixtures.assign(tenA, english, teacher);
        student = fixtures.student("api_adm_s1", tenA, "1");
        fixtures.student("api_adm_s2", tenB, "1");
        admin = accessToken(mvc, "api_admin");
    }

    // Two other test classes commit rows on purpose, so counts and list positions are checked
    // against the database rather than assumed from this class's own setUp.

    @Test
    void schoolDataIsReadable() throws Exception {
        mvc.perform(get("/api/v1/admin/overview").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students").value(studentRepository.count()))
                .andExpect(jsonPath("$.teachers").value(teacherRepository.count()))
                .andExpect(jsonPath("$.classes").value(classRepository.count()));

        mvc.perform(get("/api/v1/admin/students").param("classId", tenA.getId().toString()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("api_adm_s1"));

        mvc.perform(get("/api/v1/admin/classes/{id}", tenA.getId()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classTeacher.name").value("Teacher api_adm_teacher"))
                .andExpect(jsonPath("$.students", hasSize(1)))
                .andExpect(jsonPath("$.subjectTeachers[0].subject.name").value("ApiEnglish"));

        mvc.perform(get("/api/v1/admin/classes").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.schoolClass.name == '10-A')].studentCount", contains(1)));

        mvc.perform(get("/api/v1/admin/teachers").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Teacher api_adm_teacher")));
    }

    @Test
    void unknownRecordsAre404Json() throws Exception {
        mvc.perform(get("/api/v1/admin/students/{id}", 999999).with(bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Student not found"));
    }

    @Test
    void schoolWideAnnouncementReachesStudents() throws Exception {
        String posted = mvc.perform(post("/api/v1/admin/announcements").with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Holiday\",\"content\":\"School closed Friday\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("School office"))
                .andExpect(jsonPath("$.targetClass").isEmpty())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/student/announcements").with(bearer(accessToken(mvc, "api_adm_s1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Holiday"));

        Integer id = JsonPath.read(posted, "$.id");
        mvc.perform(delete("/api/v1/admin/announcements/{id}", id).with(bearer(admin)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/announcements").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]", hasSize(0)));
    }

    @Test
    void reportsAndPdfs() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/attendance").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classes", hasSize((int) classRepository.count())))
                .andExpect(jsonPath("$.classes[?(@.schoolClass.name == '10-A')].tally.present", contains(0)));
        mvc.perform(get("/api/v1/admin/reports/classes/{id}", tenA.getId())
                        .param("from", "2026-01-01").param("to", "2026-01-31").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-01-01"))
                .andExpect(jsonPath("$.students", hasSize(1)));
        mvc.perform(get("/api/v1/admin/students/{id}/report-card", student.getId()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void teachersCannotUseAdminEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/students").with(bearer(accessToken(mvc, "api_adm_teacher"))))
                .andExpect(status().isForbidden());
    }
}
