package com.example.rag.ingestion.parser;

import com.example.rag.persistence.entity.DocumentEntity;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 基础解析器。
 *
 * 当前版本按页抽取文本，并把每页视为一个 section。
 */
@Component
public class PdfDocumentTextParser implements DocumentTextParser {

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(DocumentEntity document, Path path) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<ParsedSection> sections = new ArrayList<>();
            String documentTitle = deriveDefaultTitle(document);
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String content = normalizePageContent(stripper.getText(pdf));
                if (!content.isEmpty()) {
                    sections.add(new ParsedSection(documentTitle + " - Page " + page, content));
                }
            }
            return new ParsedDocument("pdfbox", documentTitle, sections);
        }
    }

    private String deriveDefaultTitle(DocumentEntity document) {
        String displayName = document.getDisplayName();
        return displayName == null || displayName.isBlank() ? document.getFileName() : displayName;
    }

    private String normalizePageContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized;
    }
}
