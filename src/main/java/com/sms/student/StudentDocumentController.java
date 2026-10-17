package com.sms.student;

import java.security.Principal;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.sms.common.Downloads;
import com.sms.report.PdfService;
import com.sms.school.Student;

/** A student's own report card and ID card as PDFs. */
@Controller
public class StudentDocumentController {

    private final StudentPortalService service;
    private final PdfService pdfs;

    public StudentDocumentController(StudentPortalService service, PdfService pdfs) {
        this.service = service;
        this.pdfs = pdfs;
    }

    @GetMapping("/student/report-card")
    public ResponseEntity<Resource> reportCard(Principal principal) {
        Student s = service.currentStudent(principal.getName());
        return Downloads.pdf(pdfs.reportCard(s), "report-card.pdf");
    }

    @GetMapping("/student/id-card")
    public ResponseEntity<Resource> idCard(Principal principal) {
        Student s = service.currentStudent(principal.getName());
        return Downloads.pdf(pdfs.idCard(s), "id-card.pdf");
    }
}
