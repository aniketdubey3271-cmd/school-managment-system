package com.sms.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sms.school.ExamType;
import com.sms.school.Grade;
import com.sms.school.Student;

/** Report cards (A4) and student ID cards (credit-card size) as PDF bytes. */
@Service
public class PdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color HEADER_BG = new Color(230, 236, 245);

    /** ISO/IEC 7810 ID-1 card: 85.6 x 54 mm in points. */
    private static final Rectangle ID_CARD = new Rectangle(242.6f, 153.0f);

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font BOLD = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 10);
    private static final Font MUTED = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);

    private final ReportService reports;
    private final String schoolName;

    public PdfService(ReportService reports, @Value("${sms.school.name}") String schoolName) {
        this.reports = reports;
        this.schoolName = schoolName;
    }

    public byte[] reportCard(Student student) {
        ReportService.ReportCard card = reports.reportCard(student);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(centered(schoolName, TITLE));
        doc.add(centered("Report Card", HEADING));
        doc.add(spacer());

        PdfPTable info = new PdfPTable(new float[] {1, 2});
        info.setWidthPercentage(60);
        info.setHorizontalAlignment(Element.ALIGN_LEFT);
        infoRow(info, "Name", student.getName());
        infoRow(info, "Class", student.getSchoolClass() == null ? "-" : student.getSchoolClass().getDisplayName());
        infoRow(info, "Roll number", student.getRollNo());
        infoRow(info, "Date of birth", student.getDob() == null ? "-" : student.getDob().format(DATE));
        doc.add(info);
        doc.add(spacer());

        ReportService.Tally att = card.attendance();
        doc.add(new Paragraph("Attendance", HEADING));
        doc.add(new Paragraph(att.percent() == null ? "No attendance recorded yet."
                : att.percent() + "%  (" + att.present() + " present, " + att.absent() + " absent)", NORMAL));
        doc.add(spacer());

        if (card.gradesByExam().isEmpty()) {
            doc.add(new Paragraph("Marks", HEADING));
            doc.add(new Paragraph("No marks recorded yet.", NORMAL));
        }
        for (var entry : card.gradesByExam().entrySet()) {
            ExamType exam = entry.getKey();
            doc.add(new Paragraph(exam.getLabel(), HEADING));
            doc.add(gradeTable(entry.getValue(), card.overallPercent().get(exam)));
            doc.add(spacer());
        }

        doc.add(new Paragraph("Generated on " + LocalDate.now().format(DATE), MUTED));
        doc.close();
        return out.toByteArray();
    }

    public byte[] idCard(Student student) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(ID_CARD, 10, 10, 10, 10);
        PdfWriter.getInstance(doc, out);
        doc.open();

        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);
        PdfPCell header = new PdfPCell(new Phrase(schoolName, new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE)));
        header.setBackgroundColor(new Color(13, 110, 253));
        header.setHorizontalAlignment(Element.ALIGN_CENTER);
        header.setPadding(5);
        header.setBorder(Rectangle.NO_BORDER);
        card.addCell(header);

        PdfPCell body = new PdfPCell();
        body.setBorder(Rectangle.NO_BORDER);
        body.setPaddingTop(6);
        body.addElement(new Paragraph("STUDENT ID CARD", new Font(Font.HELVETICA, 7, Font.BOLD, Color.GRAY)));
        body.addElement(new Paragraph(student.getName(), new Font(Font.HELVETICA, 12, Font.BOLD)));
        Font small = new Font(Font.HELVETICA, 8);
        body.addElement(new Paragraph("Class: " + (student.getSchoolClass() == null ? "-"
                : student.getSchoolClass().getDisplayName()) + "     Roll no: " + student.getRollNo(), small));
        body.addElement(new Paragraph("Date of birth: " + (student.getDob() == null ? "-" : student.getDob().format(DATE)), small));
        body.addElement(new Paragraph("Student ID: " + idNumber(student), small));
        card.addCell(body);

        doc.add(card);
        doc.close();
        return out.toByteArray();
    }

    /** e.g. S00042 */
    public static String idNumber(Student student) {
        return String.format("S%05d", student.getId());
    }

    private static PdfPTable gradeTable(List<Grade> grades, Integer overall) {
        PdfPTable t = new PdfPTable(new float[] {3, 1, 1, 1});
        t.setWidthPercentage(100);
        for (String h : new String[] {"Subject", "Marks", "Max", "%"}) {
            PdfPCell c = cell(h, BOLD);
            c.setBackgroundColor(HEADER_BG);
            t.addCell(c);
        }
        for (Grade g : grades) {
            t.addCell(cell(g.getSubject().getName(), NORMAL));
            t.addCell(cell(g.getMarks().stripTrailingZeros().toPlainString(), NORMAL));
            t.addCell(cell(g.getMaxMarks().stripTrailingZeros().toPlainString(), NORMAL));
            t.addCell(cell(g.getPercent() + "%", NORMAL));
        }
        PdfPCell label = cell("Overall", BOLD);
        label.setColspan(3);
        t.addCell(label);
        t.addCell(cell(overall + "%", BOLD));
        return t;
    }

    private static void infoRow(PdfPTable t, String label, String value) {
        PdfPCell l = cell(label, BOLD);
        PdfPCell v = cell(value, NORMAL);
        l.setBorder(Rectangle.NO_BORDER);
        v.setBorder(Rectangle.NO_BORDER);
        t.addCell(l);
        t.addCell(v);
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(5);
        return c;
    }

    private static Paragraph centered(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static Paragraph spacer() {
        return new Paragraph(" ", NORMAL);
    }
}
