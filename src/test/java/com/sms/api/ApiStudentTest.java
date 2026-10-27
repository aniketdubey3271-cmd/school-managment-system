package com.sms.api;

import static com.sms.api.ApiTestSupport.accessToken;
import static com.sms.api.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sms.Fixtures;
import com.sms.school.Assignment;
import com.sms.school.Attendance;
import com.sms.school.AttendanceRepository;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.GradeRepository;
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.Subject;
import com.sms.school.Teacher;

/** "api_s1" is in 8-A; "api_s2" is in 8-B. Each test checks api_s1 only ever reaches their own data. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
        "sms.demo-data.enabled=false",
        "sms.uploads.dir=${java.io.tmpdir}/sms-test-uploads"})
@AutoConfigureMockMvc
@Transactional
class ApiStudentTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired AttendanceRepository attendance;
    @Autowired GradeRepository grades;

    Assignment ownAssignment;
    Assignment otherClassAssignment;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        Teacher teacher = fixtures.teacher("api_st_teacher");
        SchoolClass a = fixtures.schoolClass("8", "A", teacher);
        SchoolClass b = fixtures.schoolClass("8", "B", teacher);
        Subject maths = fixtures.subject("ApiMaths");
        Student s1 = fixtures.student("api_s1", a, "1");
        Student s2 = fixtures.student("api_s2", b, "1");

        mark(s1, LocalDate.now().minusDays(2), AttendanceStatus.PRESENT);
        mark(s1, LocalDate.now().minusDays(1), AttendanceStatus.ABSENT);
        mark(s2, LocalDate.now().minusDays(1), AttendanceStatus.ABSENT);
        grade(s1, maths, "45");
        grade(s2, maths, "5");

        ownAssignment = fixtures.assignment(teacher, a, maths, "Fractions", LocalDateTime.now().plusDays(3));
        otherClassAssignment = fixtures.assignment(teacher, b, maths, "Not yours", LocalDateTime.now().plusDays(3));
        token = accessToken(mvc, "api_s1");
    }

    @Test
    void dashboardSummarisesTheStudentsOwnData() throws Exception {
        mvc.perform(get("/api/v1/student/dashboard").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.name").value("Student api_s1"))
                .andExpect(jsonPath("$.student.studentId").exists())
                .andExpect(jsonPath("$.attendance.present").value(1))
                .andExpect(jsonPath("$.attendance.absent").value(1))
                .andExpect(jsonPath("$.attendance.percent").value(50))
                .andExpect(jsonPath("$.pendingAssignments").value(1));
    }

    @Test
    void attendanceAndGradesAreOnlyTheStudentsOwn() throws Exception {
        mvc.perform(get("/api/v1/student/attendance").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records", hasSize(2)))
                .andExpect(jsonPath("$.records[0].status").value("ABSENT"))
                .andExpect(jsonPath("$.records[0].date").value(LocalDate.now().minusDays(1).toString()));

        mvc.perform(get("/api/v1/student/grades").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].exam.code").value("MIDTERM"))
                .andExpect(jsonPath("$[0].grades[0].subject.name").value("ApiMaths"))
                .andExpect(jsonPath("$[0].grades[0].percent").value(90));
    }

    @Test
    void assignmentsAreLimitedToTheStudentsClass() throws Exception {
        mvc.perform(get("/api/v1/student/assignments").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].assignment.title").value("Fractions"))
                .andExpect(jsonPath("$[0].submission").isEmpty());

        mvc.perform(get("/api/v1/student/assignments/{id}", otherClassAssignment.getId()).with(bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/assignments/{id}", 999999).with(bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Assignment not found"));
    }

    @Test
    void submitAndDownloadOwnFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "essay.pdf", "application/pdf", "my essay".getBytes());
        mvc.perform(multipart("/api/v1/student/assignments/{id}/submission", ownAssignment.getId())
                        .file(file).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.originalFilename").value("essay.pdf"))
                .andExpect(jsonPath("$.allowedFileTypes", hasSize(8)))
                .andExpect(jsonPath("$.maxFileBytes").value(10 * 1024 * 1024));

        mvc.perform(get("/api/v1/student/assignments/{id}/submission/file", ownAssignment.getId()).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("essay.pdf")))
                .andExpect(content().bytes("my essay".getBytes()));
    }

    @Test
    void uploadRulesMatchTheWebsite() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1});
        mvc.perform(multipart("/api/v1/student/assignments/{id}/submission", ownAssignment.getId())
                        .file(exe).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Allowed file types")));

        mvc.perform(multipart("/api/v1/student/assignments/{id}/submission", otherClassAssignment.getId())
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1}))
                        .with(bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pdfsAndAnnouncements() throws Exception {
        mvc.perform(get("/api/v1/student/report-card").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/v1/student/id-card").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/v1/student/announcements").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
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
