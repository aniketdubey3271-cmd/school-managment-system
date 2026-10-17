package com.sms.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.sms.Fixtures;
import com.sms.school.Attendance;
import com.sms.school.AttendanceRepository;
import com.sms.school.AttendanceStatus;
import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.GradeRepository;
import com.sms.school.SchoolClass;
import com.sms.school.Student;
import com.sms.school.Subject;

/**
 * Class 3-A: class teacher "rp_ct" teaches RpMaths, "rp_st" teaches RpScience; students r1, r2.
 * Class 3-B: class teacher "rp_ot", student r3. Each test's data is rolled back.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
    "sms.demo-data.enabled=false",
        "sms.uploads.dir=${java.io.tmpdir}/sms-test-uploads",
        "sms.school.name=Test High School"})
@AutoConfigureMockMvc
@Transactional
class ReportTest {

    private static final RequestPostProcessor ADMIN = user("rp_admin").roles("ADMIN");

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired ReportService reports;
    @Autowired AttendanceRepository attendance;
    @Autowired GradeRepository grades;

    SchoolClass classA;
    SchoolClass classB;
    Subject maths;
    Subject science;
    Student r1;
    Student r2;

    @BeforeEach
    void setUp() {
        var ct = fixtures.teacher("rp_ct");
        var st = fixtures.teacher("rp_st");
        classA = fixtures.schoolClass("3", "A", ct);
        classB = fixtures.schoolClass("3", "B", fixtures.teacher("rp_ot"));
        maths = fixtures.subject("RpMaths");
        science = fixtures.subject("RpScience");
        fixtures.assign(classA, maths, ct);
        fixtures.assign(classA, science, st);
        r1 = fixtures.student("r1", classA, "1");
        r2 = fixtures.student("r2", classA, "2");
        fixtures.student("r3", classB, "1");

        LocalDate today = LocalDate.now();
        mark(r1, today.minusDays(1), AttendanceStatus.PRESENT);
        mark(r1, today.minusDays(2), AttendanceStatus.PRESENT);
        mark(r1, today.minusDays(3), AttendanceStatus.ABSENT);
        mark(r2, today.minusDays(1), AttendanceStatus.PRESENT);
        mark(r1, today.minusDays(40), AttendanceStatus.ABSENT); // outside the default 30-day range

        grade(r1, maths, "40", "50");   // 80%
        grade(r2, maths, "30", "50");   // 60%
        grade(r1, science, "9", "10");  // 90%
    }

    @Test
    void dateRangeDefaultsAndSwaps() {
        LocalDate today = LocalDate.now();
        assertThat(DateRange.of(null, null)).isEqualTo(new DateRange(today.minusDays(29), today));
        assertThat(DateRange.of(today, today.minusDays(5))).isEqualTo(new DateRange(today.minusDays(5), today));
    }

    @Test
    void classAttendanceCountsOnlyTheRange() {
        var report = reports.classAttendance(classA, DateRange.of(null, null));
        assertThat(report.tally()).isEqualTo(new ReportService.Tally(3, 1));
        assertThat(report.tally().percent()).isEqualTo(75);
        assertThat(report.students()).hasSize(2);
        assertThat(report.students().get(0).tally()).isEqualTo(new ReportService.Tally(2, 1));

        var wider = reports.classAttendance(classA, DateRange.of(LocalDate.now().minusDays(60), null));
        assertThat(wider.tally()).isEqualTo(new ReportService.Tally(3, 2));
    }

    @Test
    void schoolAttendanceHasARowPerClass() {
        var rows = reports.schoolAttendance(DateRange.of(null, null));
        var a = rows.stream().filter(r -> r.schoolClass().getId().equals(classA.getId())).findFirst().orElseThrow();
        var b = rows.stream().filter(r -> r.schoolClass().getId().equals(classB.getId())).findFirst().orElseThrow();
        assertThat(a.tally().percent()).isEqualTo(75);
        assertThat(b.tally().percent()).isNull();
    }

    @Test
    void classPerformanceAveragesPercentages() {
        var perf = reports.classPerformance(classA, s -> true);
        assertThat(perf.exams()).containsExactly(ExamType.MIDTERM);
        var mathsRow = perf.subjects().stream().filter(p -> p.subject().getId().equals(maths.getId())).findFirst().orElseThrow();
        assertThat(mathsRow.byExam().get(ExamType.MIDTERM)).isEqualTo(new ReportService.ExamStat(70, 2));

        var onlyScience = reports.classPerformance(classA, s -> s.getId().equals(science.getId()));
        assertThat(onlyScience.subjects()).extracting(p -> p.subject().getName()).containsExactly("RpScience");
    }

    @Test
    void reportCardOverallIsTotalMarksOverTotalMax() {
        var card = reports.reportCard(r1);
        assertThat(card.overallPercent().get(ExamType.MIDTERM)).isEqualTo(82); // 49 / 60
        assertThat(card.attendance()).isEqualTo(new ReportService.Tally(2, 2));
    }

    @Test
    void reportPagesRenderAndTeacherSeesOnlyTheirSubjects() throws Exception {
        mvc.perform(get("/admin/dashboard").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/admin/reports").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/admin/reports").param("from", "2020-01-01").param("to", "2020-01-31").with(ADMIN))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/reports/classes/" + classA.getId()).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RpMaths")));
        mvc.perform(get("/admin/classes/" + classA.getId()).with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/admin/students").with(ADMIN)).andExpect(status().isOk());

        String url = "/teacher/classes/" + classA.getId() + "/report";
        mvc.perform(get(url).with(user("rp_ct").roles("TEACHER")))
                .andExpect(content().string(containsString("RpMaths")))
                .andExpect(content().string(containsString("RpScience")));
        mvc.perform(get(url).with(user("rp_st").roles("TEACHER")))
                .andExpect(content().string(containsString("RpScience")))
                .andExpect(content().string(not(containsString("RpMaths"))));
        mvc.perform(get(url).with(user("rp_ot").roles("TEACHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/classes").with(user("rp_ct").roles("TEACHER"))).andExpect(status().isOk());
    }

    @Test
    void pdfsAreRealAndContainTheStudentsData() throws Exception {
        byte[] card = mvc.perform(get("/admin/students/" + r1.getId() + "/report-card").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        String text = pdfText(card);
        assertThat(text).contains("Test High School", "Report Card", "Student r1", "RpMaths", "RpScience", "82%");

        byte[] id = mvc.perform(get("/student/id-card").with(user("r1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdfText(id)).contains("Student r1", "STUDENT ID CARD", PdfService.idNumber(r1));

        mvc.perform(get("/student/report-card").with(user("r1").roles("STUDENT")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/admin/students/" + r2.getId() + "/report-card").with(user("r1").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    private static String pdfText(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        StringBuilder sb = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            sb.append(extractor.getTextFromPage(page)).append('\n');
        }
        return sb.toString();
    }

    private void mark(Student s, LocalDate date, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.setStudent(s);
        a.setDate(date);
        a.setStatus(status);
        attendance.save(a);
    }

    private void grade(Student s, Subject subject, String marks, String max) {
        Grade g = new Grade();
        g.setStudent(s);
        g.setSubject(subject);
        g.setExamType(ExamType.MIDTERM);
        g.setMarks(new BigDecimal(marks));
        g.setMaxMarks(new BigDecimal(max));
        grades.save(g);
    }
}
