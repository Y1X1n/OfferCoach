package com.offercoach.interview.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsParagraphsAndTablesFromDocx() throws IOException {
        byte[] docx = buildDocx();

        ExtractedDocument document = extractor.extract(docx, "docx");

        assertThat(document.text()).contains("Hello DOCX").contains("Cell A").contains("Cell B");
        assertThat(document.pageCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void extractsTextFromPdf() throws IOException {
        byte[] pdf = buildPdf();

        ExtractedDocument document = extractor.extract(pdf, "pdf");

        assertThat(document.text()).contains("Hello PDF Resume");
        assertThat(document.pageCount()).isEqualTo(1);
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> extractor.extract("plain text".getBytes(), "txt"))
                .isInstanceOf(DocumentExtractionException.class);
    }

    private static byte[] buildDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Hello DOCX");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Cell A");
            table.getRow(0).getCell(1).setText("Cell B");
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] buildPdf() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Hello PDF Resume");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
