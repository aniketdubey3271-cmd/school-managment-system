package com.sms.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sms.Fixtures;
import com.sms.school.AttendanceRepository;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.GradeRepository;
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.Subject;

import jakarta.persistence.EntityManager;

/**
 * Class 7-A: "classteach" is class teacher and teaches Maths; "scienceteach" teaches Science only.
 * Class 7-B belongs to "otherteach". Each test's data is rolled back afterwards.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on")
@AutoConfigureMockMvc
@Transactional
class TeacherPortalTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired AttendanceRepository attendance;
    @Autowired GradeRepository grades;
    @Autowired EntityManager em;

    SchoolClass classA;
    SchoolClass classB;
    Subject maths;
    Subject science;
    Student ravi;
    Student meena;
    Student otherClassStudent;

    @BeforeEach
    void setUp() {
        var classTeacher = fixtures.teacher("classteach");
        var scienceTeacher = fixtures.teacher("scienceteach");
        var otherTeacher = fixtures.teacher("otherteach");
        classA = fixtures.schoolClass("7", "A", classTeacher);
        classB = fixtures.schoolClass("7", "B", otherTeacher);
        maths = fixtures.subject("Maths");
        science = fixtures.subject("Science");
        fixtures.assign(classA, maths, classTeacher);
        fixtures.assign(classA, science, scienceTeacher);
        ravi = fixtures.student("ravi", classA, "1");
        meena = fixtures.student("meena", classA, "2");
        otherClassStudent = fixtures.student("bstudent", classB, "1");
    }

    @Test
    @WithMockUser(username = "classteach", roles = "TEACHER")
    void classTeacherPagesRender() throws Exception {
        mvc.perform(get("/teacher/classes"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("7-A")));
        mvc.perform(get("/teacher/classes/" + classA.getId() + "/students")).andExpect(status().isOk());
        mvc.perform(get("/teacher/classes/" + classA.getId() + "/attendance")).andExpect(status().isOk());
        mvc.perform(get(gradesUrl(maths))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "classteach", roles = "TEACHER")
    void attendanceIsSavedThenUpdatedNotDuplicated() throws Exception {
        LocalDate day = LocalDate.now().minusDays(1);
        String url = "/teacher/classes/" + classA.getId() + "/attendance";

        mvc.perform(post(url).with(csrf()).param("date", day.toString())
                        .param("statuses[" + ravi.getId() + "]", "PRESENT")
                        .param("statuses[" + meena.getId() + "]", "ABSENT")
                        .param("statuses[" + otherClassStudent.getId() + "]", "ABSENT"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(url).with(csrf()).param("date", day.toString())
                        .param("statuses[" + meena.getId() + "]", "PRESENT"))
                .andExpect(status().is3xxRedirection());

        em.flush();
        em.clear();
        var saved = attendance.findByStudentSchoolClassAndDate(classA, day);
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(a -> a.getStatus() == AttendanceStatus.PRESENT);
        assertThat(saved).allMatch(a -> a.getDate().equals(day));
        assertThat(attendance.findByStudentSchoolClassAndDate(classB, day)).isEmpty();
    }

    @Test
    @WithMockUser(username = "classteach", roles = "TEACHER")
    void futureAttendanceIsRejected() throws Exception {
        mvc.perform(post("/teacher/classes/" + classA.getId() + "/attendance").with(csrf())
                        .param("date", LocalDate.now().plusDays(1).toString())
                        .param("statuses[" + ravi.getId() + "]", "PRESENT"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
        assertThat(attendance.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "scienceteach", roles = "TEACHER")
    void subjectTeacherCanGradeButNotMarkAttendance() throws Exception {
        mvc.perform(get("/teacher/classes/" + classA.getId() + "/students")).andExpect(status().isOk());
        mvc.perform(get(gradesUrl(science))).andExpect(status().isOk());
        mvc.perform(get("/teacher/classes/" + classA.getId() + "/attendance")).andExpect(status().isForbidden());
        mvc.perform(get(gradesUrl(maths))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "otherteach", roles = "TEACHER")
    void teacherCannotOpenAnotherTeachersClass() throws Exception {
        mvc.perform(get("/teacher/classes/" + classA.getId() + "/students")).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/classes/" + classA.getId() + "/attendance")).andExpect(status().isForbidden());
        mvc.perform(post("/teacher/classes/" + classA.getId() + "/attendance").with(csrf())
                        .param("date", LocalDate.now().toString())
                        .param("statuses[" + ravi.getId() + "]", "ABSENT"))
                .andExpect(status().isForbidden());
        mvc.perform(post(gradesUrl(maths)).with(csrf())
                        .param("examType", "MIDTERM").param("maxMarks", "100")
                        .param("marks[" + ravi.getId() + "]", "0"))
                .andExpect(status().isForbidden());
        assertThat(attendance.findAll()).isEmpty();
        assertThat(grades.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "classteach", roles = "TEACHER")
    void gradesAreSavedWithDecimalsAndBlankIsSkipped() throws Exception {
        mvc.perform(post(gradesUrl(maths)).with(csrf())
                        .param("examType", "MIDTERM").param("maxMarks", "50")
                        .param("marks[" + ravi.getId() + "]", "42.5")
                        .param("marks[" + meena.getId() + "]", ""))
                .andExpect(status().is3xxRedirection());

        em.flush();
        em.clear();
        var saved = grades.findByStudentSchoolClassAndSubjectAndExamType(classA, maths, ExamType.MIDTERM);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStudent().getId()).isEqualTo(ravi.getId());
        assertThat(saved.get(0).getMarks()).isEqualByComparingTo(new BigDecimal("42.5"));
        assertThat(saved.get(0).getMaxMarks()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @WithMockUser(username = "classteach", roles = "TEACHER")
    void marksAboveMaxRejectWholeSubmission() throws Exception {
        mvc.perform(post(gradesUrl(maths)).with(csrf())
                        .param("examType", "FINAL").param("maxMarks", "50")
                        .param("marks[" + ravi.getId() + "]", "40")
                        .param("marks[" + meena.getId() + "]", "51"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
        assertThat(grades.findAll()).isEmpty();
    }

    private String gradesUrl(Subject subject) {
        return "/teacher/classes/" + classA.getId() + "/subjects/" + subject.getId() + "/grades";
    }
}
