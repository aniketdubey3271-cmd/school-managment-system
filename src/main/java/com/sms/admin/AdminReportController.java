package com.sms.admin;

import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.sms.common.Downloads;
import com.sms.report.DateRange;
import com.sms.report.PdfService;
import com.sms.report.ReportService;
import com.sms.school.SchoolClass;
import com.sms.school.Student;

@Controller
public class AdminReportController {

    private final ReportService reports;
    private final PdfService pdfs;
    private final ClassAdminService classService;
    private final StudentAdminService studentService;

    public AdminReportController(ReportService reports, PdfService pdfs, ClassAdminService classService,
                                 StudentAdminService studentService) {
        this.reports = reports;
        this.pdfs = pdfs;
        this.classService = classService;
        this.studentService = studentService;
    }

    /** School-wide attendance by class for a date range. */
    @GetMapping("/admin/reports")
    public String school(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         Model model) {
        DateRange range = DateRange.of(from, to);
        model.addAttribute("range", range);
        model.addAttribute("rows", reports.schoolAttendance(range));
        return "admin/reports/school";
    }

    /** One class: attendance per student and performance in every subject. */
    @GetMapping("/admin/reports/classes/{id}")
    public String classReport(@PathVariable Long id,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              Model model) {
        SchoolClass schoolClass = classService.getClass(id);
        DateRange range = DateRange.of(from, to);
        model.addAttribute("range", range);
        model.addAttribute("attendance", reports.classAttendance(schoolClass, range));
        model.addAttribute("performance", reports.classPerformance(schoolClass, subject -> true));
        return "admin/reports/class";
    }

    @GetMapping("/admin/students/{id}/report-card")
    public ResponseEntity<Resource> reportCard(@PathVariable Long id) {
        Student s = studentService.get(id);
        return Downloads.pdf(pdfs.reportCard(s), "report-card-" + s.getUser().getUsername() + ".pdf");
    }

    @GetMapping("/admin/students/{id}/id-card")
    public ResponseEntity<Resource> idCard(@PathVariable Long id) {
        Student s = studentService.get(id);
        return Downloads.pdf(pdfs.idCard(s), "id-card-" + s.getUser().getUsername() + ".pdf");
    }
}
