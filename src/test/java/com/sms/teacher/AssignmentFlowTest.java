package com.sms.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sms.Fixtures;
import com.sms.school.Assignment;
import com.sms.school.AssignmentRepository;
import com.sms.school.SchoolClass;
import com.sms.school.Subject;
import com.sms.school.SubmissionRepository;
import com.sms.school.Teacher;
import com.sms.school.TeachingAssignmentRepository;

/**
 * Each test builds its own class with unique names: teacher "own" teaches the subject there,
 * teacher "other" does not; student "in" is in the class, student "out" is in a different class.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
        "sms.demo-data.enabled=false",
        "sms.uploads.dir=${java.io.tmpdir}/sms-test-uploads"})
@AutoConfigureMockMvc
@Transactional
class AssignmentFlowTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired AssignmentRepository assignments;
    @Autowired SubmissionRepository submissions;
    @Autowired TeachingAssignmentRepository teaching;
    @Value("${sms.uploads.dir}") String uploadsDir;

    String id;
    Teacher own;
    SchoolClass schoolClass;
    Subject subject;

    void setUp() {
        id = UUID.randomUUID().toString().substring(0, 6);
        own = fixtures.teacher("own" + id);
        fixtures.teacher("other" + id);
        schoolClass = fixtures.schoolClass("5", "A" + id, null);
        SchoolClass otherClass = fixtures.schoolClass("5", "B" + id, null);
        subject = fixtures.subject("Subj" + id);
        fixtures.assign(schoolClass, subject, own);
        fixtures.student("in" + id, schoolClass, "1");
        fixtures.student("out" + id, otherClass, "1");
    }

    @Test
    void teacherCreatesAssignmentOnlyForOwnSubjectAndFutureDate() throws Exception {
        setUp();
        Long teachingId = teaching.findBySchoolClassIdAndSubjectId(schoolClass.getId(), subject.getId()).orElseThrow().getId();
        String future = LocalDateTime.now().plusDays(3).withSecond(0).withNano(0).toString();

        mvc.perform(post("/teacher/assignments").with(as("own")).with(csrf())
                        .param("teachingId", teachingId.toString()).param("title", "Essay")
                        .param("dueDate", future))
                .andExpect(status().is3xxRedirection());
        assertThat(assignments.findByTeacherOrderByDueDateDesc(own)).extracting(Assignment::getTitle).containsExactly("Essay");

        mvc.perform(post("/teacher/assignments").with(as("other")).with(csrf())
                        .param("teachingId", teachingId.toString()).param("title", "Sneaky")
                        .param("dueDate", future))
                .andExpect(model().attributeHasFieldErrors("form", "teachingId"));

        mvc.perform(post("/teacher/assignments").with(as("own")).with(csrf())
                        .param("teachingId", teachingId.toString()).param("title", "Late")
                        .param("dueDate", LocalDateTime.now().minusHours(1).withSecond(0).withNano(0).toString()))
                .andExpect(model().attributeHasFieldErrors("form", "dueDate"));
    }

    @Test
    void teacherPagesRenderAndOtherTeacherIsBlocked() throws Exception {
        setUp();
        Assignment a = fixtures.assignment(own, schoolClass, subject, "Worksheet", LocalDateTime.now().plusDays(1));
        for (String url : new String[] {"/teacher/dashboard", "/teacher/assignments", "/teacher/assignments/new",
                "/teacher/assignments/" + a.getId(), "/teacher/assignments/" + a.getId() + "/edit"}) {
            mvc.perform(get(url).with(as("own"))).andExpect(status().isOk());
        }
        mvc.perform(get("/teacher/assignments/" + a.getId()).with(as("other"))).andExpect(status().isForbidden());
    }

    @Test
    void studentSubmitsResubmitsAndTeacherDownloads() throws Exception {
        setUp();
        Assignment a = fixtures.assignment(own, schoolClass, subject, "Worksheet", LocalDateTime.now().plusDays(1));
        String base = "/student/assignments/" + a.getId();

        mvc.perform(get("/student/assignments").with(asStudent("in")))
                .andExpect(content().string(containsString("Worksheet")));
        mvc.perform(get(base).with(asStudent("in"))).andExpect(status().isOk());

        mvc.perform(multipart(base + "/submit").file(pdf("first.pdf", "v1")).with(asStudent("in")).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(multipart(base + "/submit").file(pdf("second.pdf", "v2")).with(asStudent("in")).with(csrf()))
                .andExpect(flash().attributeExists("success"));

        var saved = submissions.findByAssignment(a);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getOriginalFilename()).isEqualTo("second.pdf");
        assertThat(Files.readString(Path.of(uploadsDir, saved.get(0).getStoredName()))).isEqualTo("v2");

        mvc.perform(get(base + "/file").with(asStudent("in")))
                .andExpect(status().isOk())
                .andExpect(content().string("v2"))
                .andExpect(header().string("Content-Disposition", containsString("second.pdf")));
        String teacherFile = "/teacher/assignments/" + a.getId() + "/submissions/" + saved.get(0).getId() + "/file";
        mvc.perform(get(teacherFile).with(as("own"))).andExpect(content().string("v2"));
        mvc.perform(get(teacherFile).with(as("other"))).andExpect(status().isForbidden());
    }

    @Test
    void submissionRulesAreEnforced() throws Exception {
        setUp();
        Assignment open = fixtures.assignment(own, schoolClass, subject, "Open", LocalDateTime.now().plusDays(1));
        Assignment closed = fixtures.assignment(own, schoolClass, subject, "Closed", LocalDateTime.now().minusMinutes(1));

        mvc.perform(get("/student/assignments/" + open.getId()).with(asStudent("out"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/student/assignments/" + open.getId() + "/submit").file(pdf("x.pdf", "x"))
                        .with(asStudent("out")).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/student/assignments/" + open.getId() + "/submit")
                        .file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1}))
                        .with(asStudent("in")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Allowed file types")));

        mvc.perform(multipart("/student/assignments/" + closed.getId() + "/submit").file(pdf("late.pdf", "x"))
                        .with(asStudent("in")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("due date has passed")));

        assertThat(submissions.findByAssignment(open)).isEmpty();
        assertThat(submissions.findByAssignment(closed)).isEmpty();
    }

    /** Outside the test transaction: the refused delete must really roll back. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void assignmentWithSubmissionsCannotBeDeleted() throws Exception {
        setUp();
        Assignment a = fixtures.assignment(own, schoolClass, subject, "Keep", LocalDateTime.now().plusDays(1));
        mvc.perform(multipart("/student/assignments/" + a.getId() + "/submit").file(pdf("work.pdf", "w"))
                        .with(asStudent("in")).with(csrf()))
                .andExpect(flash().attributeExists("success"));

        mvc.perform(post("/teacher/assignments/" + a.getId() + "/delete").with(as("own")).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertThat(assignments.findById(a.getId())).isPresent();
    }

    private RequestPostProcessor as(String teacherPrefix) {
        return user(teacherPrefix + id).roles("TEACHER");
    }

    private RequestPostProcessor asStudent(String studentPrefix) {
        return user(studentPrefix + id).roles("STUDENT");
    }

    private static MockMultipartFile pdf(String name, String body) {
        return new MockMultipartFile("file", name, "application/pdf", body.getBytes(StandardCharsets.UTF_8));
    }
}
