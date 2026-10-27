package com.sms.api;

import static com.sms.api.ApiTestSupport.accessToken;
import static com.sms.api.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.Subject;
import com.sms.school.Teacher;
import com.sms.school.TeachingAssignment;
import com.sms.school.TeachingAssignmentRepository;

/**
 * "ct" is class teacher of 9-A and teaches Physics there; "st" only teaches Chemistry in 9-A.
 * Neither has anything to do with 9-B.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
        "sms.demo-data.enabled=false",
        "sms.uploads.dir=${java.io.tmpdir}/sms-test-uploads"})
@AutoConfigureMockMvc
@Transactional
class ApiTeacherTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired TeachingAssignmentRepository teaching;

    SchoolClass nineA;
    SchoolClass nineB;
    Subject physics;
    Subject chemistry;
    Student s1;
    String ct;
    String st;

    @BeforeEach
    void setUp() throws Exception {
        Teacher classTeacher = fixtures.teacher("api_ct");
        Teacher subjectTeacher = fixtures.teacher("api_st");
        Teacher other = fixtures.teacher("api_other");
        nineA = fixtures.schoolClass("9", "A", classTeacher);
        nineB = fixtures.schoolClass("9", "B", other);
        physics = fixtures.subject("ApiPhysics");
        chemistry = fixtures.subject("ApiChemistry");
        fixtures.assign(nineA, physics, classTeacher);
        fixtures.assign(nineA, chemistry, subjectTeacher);
        fixtures.assign(nineB, physics, other);
        s1 = fixtures.student("api_t_s1", nineA, "1");
        fixtures.student("api_t_s2", nineA, "2");
        fixtures.student("api_t_s3", nineB, "1");
        ct = accessToken(mvc, "api_ct");
        st = accessToken(mvc, "api_st");
    }

    @Test
    void classesShowRoleAndSubjects() throws Exception {
        mvc.perform(get("/api/v1/teacher/classes").with(bearer(ct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].schoolClass.name").value("9-A"))
                .andExpect(jsonPath("$[0].classTeacher").value(true))
                .andExpect(jsonPath("$[0].subjects[0].name").value("ApiPhysics"));
        mvc.perform(get("/api/v1/teacher/classes/{id}/students", nineA.getId()).with(bearer(st)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/teacher/classes/{id}/students", nineB.getId()).with(bearer(st)))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheClassTeacherMarksAttendance() throws Exception {
        String today = LocalDate.now().toString();
        mvc.perform(get("/api/v1/teacher/classes/{id}/attendance", nineA.getId()).with(bearer(ct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMarked").value(false))
                .andExpect(jsonPath("$.students[0].status").value("PRESENT"));

        mvc.perform(put("/api/v1/teacher/classes/{id}/attendance", nineA.getId()).with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"statuses\":{\"" + s1.getId() + "\":\"ABSENT\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMarked").value(true))
                .andExpect(jsonPath("$.students[0].student.name").value("Student api_t_s1"))
                .andExpect(jsonPath("$.students[0].status").value("ABSENT"));

        mvc.perform(put("/api/v1/teacher/classes/{id}/attendance", nineA.getId()).with(bearer(st))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"statuses\":{}}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/teacher/classes/{id}/attendance", nineA.getId()).with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + LocalDate.now().plusDays(1) + "\",\"statuses\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Attendance can't be marked for a future date"));
    }

    @Test
    void onlyTheSubjectTeacherEntersGrades() throws Exception {
        String url = "/api/v1/teacher/classes/{c}/subjects/{s}/grades";
        mvc.perform(put(url, nineA.getId(), chemistry.getId()).with(bearer(st))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exam\":\"FINAL\",\"maxMarks\":80,\"marks\":{\"" + s1.getId() + "\":72.5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exam.code").value("FINAL"))
                .andExpect(jsonPath("$.maxMarks").value(80))
                .andExpect(jsonPath("$.students[0].marks").value(72.5))
                .andExpect(jsonPath("$.students[1].marks").isEmpty())
                .andExpect(jsonPath("$.examTypes", hasSize(4)));

        mvc.perform(put(url, nineA.getId(), chemistry.getId()).with(bearer(st))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exam\":\"FINAL\",\"maxMarks\":80,\"marks\":{\"" + s1.getId() + "\":95}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Marks for Student api_t_s1 must be between 0 and 80"));

        // The class teacher doesn't teach Chemistry, so can't grade it.
        mvc.perform(get(url, nineA.getId(), chemistry.getId()).with(bearer(ct)))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignmentLifecycle() throws Exception {
        TeachingAssignment pair = teaching.findBySchoolClassIdAndSubjectId(nineA.getId(), physics.getId()).orElseThrow();
        String due = LocalDateTime.now().plusDays(5).truncatedTo(ChronoUnit.SECONDS).toString();

        mvc.perform(get("/api/v1/teacher/teaching").with(bearer(ct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pair.getId()));

        String created = mvc.perform(post("/api/v1/teacher/assignments").with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teachingId\":" + pair.getId() + ",\"title\":\"Motion\",\"dueDate\":\"" + due + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schoolClass.name").value("9-A"))
                .andExpect(jsonPath("$.subject.name").value("ApiPhysics"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/teacher/assignments/{id}", id).with(bearer(ct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissions", hasSize(2)))
                .andExpect(jsonPath("$.submissions[0].submission").isEmpty());
        mvc.perform(get("/api/v1/teacher/assignments/{id}", id).with(bearer(st)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/teacher/assignments/{id}", id).with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Motion and force\",\"dueDate\":\"" + due + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Motion and force"));

        mvc.perform(delete("/api/v1/teacher/assignments/{id}", id).with(bearer(ct)))
                .andExpect(status().isNoContent());
    }

    @Test
    void assignmentRulesAreEnforced() throws Exception {
        TeachingAssignment notMine = teaching.findBySchoolClassIdAndSubjectId(nineB.getId(), physics.getId()).orElseThrow();
        String past = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS).toString();
        TeachingAssignment mine = teaching.findBySchoolClassIdAndSubjectId(nineA.getId(), physics.getId()).orElseThrow();

        mvc.perform(post("/api/v1/teacher/assignments").with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teachingId\":" + notMine.getId() + ",\"title\":\"X\",\"dueDate\":\"" + past + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.teachingId").exists());
        mvc.perform(post("/api/v1/teacher/assignments").with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teachingId\":" + mine.getId() + ",\"title\":\"X\",\"dueDate\":\"" + past + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dueDate").value("Due date must be in the future"));
        mvc.perform(post("/api/v1/teacher/assignments").with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teachingId\":" + mine.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.dueDate").exists());
    }

    @Test
    void announcementsGoToOwnClassesOnly() throws Exception {
        String posted = mvc.perform(post("/api/v1/teacher/announcements").with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lab day\",\"content\":\"Bring goggles\",\"targetClassId\":" + nineA.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.author").value("Teacher api_ct"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/teacher/announcements").with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"content\":\"Y\",\"targetClassId\":" + nineB.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetClassId").value("Choose one of your classes"));

        Integer id = JsonPath.read(posted, "$.id");
        mvc.perform(get("/api/v1/teacher/announcements").with(bearer(st)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mine").value(false));
        mvc.perform(delete("/api/v1/teacher/announcements/{id}", id).with(bearer(st)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/teacher/announcements/{id}", id).with(bearer(ct)))
                .andExpect(status().isNoContent());
    }

    @Test
    void reportShowsOnlySubjectsTheTeacherMaySee() throws Exception {
        mvc.perform(put("/api/v1/teacher/classes/{c}/subjects/{s}/grades", nineA.getId(), physics.getId()).with(bearer(ct))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exam\":\"MIDTERM\",\"maxMarks\":100,\"marks\":{\"" + s1.getId() + "\":60}}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/teacher/classes/{id}/report", nineA.getId()).with(bearer(ct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects[0].subject.name").value("ApiPhysics"))
                .andExpect(jsonPath("$.subjects[0].exams[0].averagePercent").value(60));
        // The Chemistry teacher can see the class report, but not Physics marks.
        mvc.perform(get("/api/v1/teacher/classes/{id}/report", nineA.getId()).with(bearer(st)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects", hasSize(0)));
    }
}
