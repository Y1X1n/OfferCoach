package com.offercoach.interview.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * PDF / DOCX 纯文本抽取；扫描件在 PDFBox 下会得到空文本，由上层判定需手动录入。
 */
@Component
public class DocumentTextExtractor {

    private static final double CHARS_PER_PAGE = 2500.0;
    private static final int MAX_PDF_PAGES = 50;
    private static final int MAX_TEXT_CHARS = 300_000;

    static {
        // 防止 DOCX zip bomb 在解压阶段耗尽内存。
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(32L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(2_000_000L);
    }

    public ExtractedDocument extract(byte[] content, String extension) {
        if (content == null || content.length == 0) {
            throw new DocumentExtractionException("文件内容为空");
        }
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        try {
            return switch (ext) {
                case "pdf" -> extractPdf(content);
                case "docx" -> extractDocx(content);
                default -> throw new DocumentExtractionException("不支持的文件类型");
            };
        } catch (IOException exception) {
            throw new DocumentExtractionException("文本抽取失败", exception);
        }
    }

    private ExtractedDocument extractPdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new DocumentExtractionException("PDF 页数超过 " + MAX_PDF_PAGES + " 页");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = limitText(stripper.getText(document));
            return new ExtractedDocument(text, document.getNumberOfPages());
        }
    }

    private ExtractedDocument extractDocx(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder builder = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    builder.append(paragraph.getText()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    appendTable(builder, table);
                }
                if (builder.length() > MAX_TEXT_CHARS) {
                    throw new DocumentExtractionException("文档文本超过 " + MAX_TEXT_CHARS + " 字符");
                }
            }
            String text = clean(builder.toString());
            int pages = Math.max(1, (int) Math.ceil(text.length() / CHARS_PER_PAGE));
            return new ExtractedDocument(text, pages);
        }
    }

    private static String limitText(String text) {
        if (text.length() > MAX_TEXT_CHARS) {
            throw new DocumentExtractionException("文档文本超过 " + MAX_TEXT_CHARS + " 字符");
        }
        return clean(text);
    }

    private void appendTable(StringBuilder builder, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder line = new StringBuilder();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                if (cellText != null && !cellText.isBlank()) {
                    line.append(cellText.strip()).append(' ');
                }
            }
            String content = line.toString().strip();
            if (!content.isEmpty()) {
                builder.append(content).append('\n');
            }
        }
    }

    /**
     * 去控制符与零宽字符、统一换行、合并多余空行。
     */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        normalized = normalized
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "");
        StringBuilder builder = new StringBuilder();
        int blankLines = 0;
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                blankLines++;
                if (blankLines > 1) {
                    continue;
                }
            } else {
                blankLines = 0;
            }
            builder.append(trimmed).append('\n');
        }
        return builder.toString().strip();
    }
}
