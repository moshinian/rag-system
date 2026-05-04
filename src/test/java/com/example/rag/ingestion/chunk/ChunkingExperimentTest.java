package com.example.rag.ingestion.chunk;

import com.example.rag.config.RagChunkingProperties;
import com.example.rag.ingestion.parser.DocumentTextParser;
import com.example.rag.ingestion.parser.MarkdownDocumentTextParser;
import com.example.rag.ingestion.parser.ParsedDocument;
import com.example.rag.ingestion.parser.PdfDocumentTextParser;
import com.example.rag.ingestion.parser.PlainTextDocumentTextParser;
import com.example.rag.persistence.entity.DocumentEntity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingExperimentTest {

    private static final int SHORT_CHUNK_THRESHOLD = 120;
    private static final int LONG_CHUNK_THRESHOLD = 500;

    private final DocumentTextParser markdownParser = new MarkdownDocumentTextParser();
    private final DocumentTextParser plainTextParser = new PlainTextDocumentTextParser();
    private final DocumentTextParser pdfParser = new PdfDocumentTextParser();

    @Test
    void shouldCompareChunkingProfilesAcrossSampleDocuments() throws Exception {
        List<ChunkingProfile> profiles = List.of(
                new ChunkingProfile("compact", 480, 60, 180),
                new ChunkingProfile("balanced", 600, 80, 240),
                new ChunkingProfile("wide", 720, 120, 300)
        );
        List<SampleDocumentSpec> samples = List.of(
                new SampleDocumentSpec("markdown", "md", Path.of("work/samples/day4-upload-sample.md")),
                new SampleDocumentSpec("plain-text", "txt", Path.of("work/samples/day4-upload-sample.txt")),
                new SampleDocumentSpec("pdf", "pdf", Path.of("work/samples/day4-upload-sample.pdf")),
                new SampleDocumentSpec("long-markdown", "md", Path.of("work/samples/day19-chunking-sample.md"))
        );

        List<ExperimentResult> results = profiles.stream()
                .flatMap(profile -> samples.stream().map(sample -> runExperiment(profile, sample)))
                .toList();

        results.forEach(result -> assertThat(result.metrics().chunkCount()).isGreaterThan(0));

        Map<String, Integer> totalChunkCountByProfile = aggregateChunkCount(results);
        assertThat(totalChunkCountByProfile.get("compact"))
                .as("smaller chunk window should not produce fewer chunks than balanced profile")
                .isGreaterThanOrEqualTo(totalChunkCountByProfile.get("balanced"));
        assertThat(totalChunkCountByProfile.get("balanced"))
                .as("balanced profile should not produce fewer chunks than wide profile")
                .isGreaterThanOrEqualTo(totalChunkCountByProfile.get("wide"));

        System.out.println(renderMarkdownReport(results, totalChunkCountByProfile));
    }

    private ExperimentResult runExperiment(ChunkingProfile profile, SampleDocumentSpec sample) {
        try {
            DocumentEntity document = createDocument(sample);
            ParsedDocument parsedDocument = parserFor(sample.fileType()).parse(document, sample.path());
            FixedWindowChunker chunker = new FixedWindowChunker(toProperties(profile));
            List<ChunkDraft> chunks = chunker.chunk(parsedDocument);
            return new ExperimentResult(profile, sample, summarize(chunks));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to run chunking experiment for "
                    + profile.name() + " / " + sample.name() + ": " + ex.getMessage(), ex);
        }
    }

    private DocumentEntity createDocument(SampleDocumentSpec sample) {
        DocumentEntity document = new DocumentEntity();
        document.setFileName(sample.path().getFileName().toString());
        document.setDisplayName("Day19 " + sample.name() + " Sample");
        document.setFileType(sample.fileType());
        return document;
    }

    private DocumentTextParser parserFor(String fileType) {
        return switch (fileType) {
            case "md" -> markdownParser;
            case "txt" -> plainTextParser;
            case "pdf" -> pdfParser;
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileType);
        };
    }

    private RagChunkingProperties toProperties(ChunkingProfile profile) {
        RagChunkingProperties properties = new RagChunkingProperties();
        properties.setStrategy("fixed-window-" + profile.name());
        properties.setMaxChunkChars(profile.maxChunkChars());
        properties.setOverlapChars(profile.overlapChars());
        properties.setMinBreakSearchOffset(profile.minBreakSearchOffset());
        return properties;
    }

    private ChunkMetrics summarize(List<ChunkDraft> chunks) {
        int chunkCount = chunks.size();
        int minChars = chunks.stream().mapToInt(chunk -> chunk.content().length()).min().orElse(0);
        int maxChars = chunks.stream().mapToInt(chunk -> chunk.content().length()).max().orElse(0);
        int totalChars = chunks.stream().mapToInt(chunk -> chunk.content().length()).sum();
        long shortChunkCount = chunks.stream().filter(chunk -> chunk.content().length() < SHORT_CHUNK_THRESHOLD).count();
        long longChunkCount = chunks.stream().filter(chunk -> chunk.content().length() > LONG_CHUNK_THRESHOLD).count();
        double averageChars = chunkCount == 0 ? 0D : (double) totalChars / chunkCount;
        return new ChunkMetrics(chunkCount, totalChars, averageChars, minChars, maxChars, shortChunkCount, longChunkCount);
    }

    private Map<String, Integer> aggregateChunkCount(List<ExperimentResult> results) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (ExperimentResult result : results) {
            String profileName = result.profile().name();
            int currentCount = totals.getOrDefault(profileName, 0);
            totals.put(profileName, currentCount + result.metrics().chunkCount());
        }
        return totals;
    }

    private String renderMarkdownReport(List<ExperimentResult> results, Map<String, Integer> totalChunkCountByProfile) {
        StringBuilder builder = new StringBuilder();
        builder.append("Day19 Chunking Experiment Report\n");
        builder.append("| profile | sample | chunks | avgChars | minChars | maxChars | short<120 | long>500 |\n");
        builder.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (ExperimentResult result : results) {
            ChunkMetrics metrics = result.metrics();
            builder.append("| ")
                    .append(result.profile().name()).append(" | ")
                    .append(result.sample().name()).append(" | ")
                    .append(metrics.chunkCount()).append(" | ")
                    .append(format(metrics.averageChars())).append(" | ")
                    .append(metrics.minChars()).append(" | ")
                    .append(metrics.maxChars()).append(" | ")
                    .append(metrics.shortChunkCount()).append(" | ")
                    .append(metrics.longChunkCount()).append(" |\n");
        }
        builder.append("Totals: ");
        totalChunkCountByProfile.forEach((profile, count) ->
                builder.append(profile).append('=').append(count).append(' '));
        return builder.toString().trim();
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record ChunkingProfile(
            String name,
            int maxChunkChars,
            int overlapChars,
            int minBreakSearchOffset
    ) {
    }

    private record SampleDocumentSpec(
            String name,
            String fileType,
            Path path
    ) {
    }

    private record ChunkMetrics(
            int chunkCount,
            int totalChars,
            double averageChars,
            int minChars,
            int maxChars,
            long shortChunkCount,
            long longChunkCount
    ) {
    }

    private record ExperimentResult(
            ChunkingProfile profile,
            SampleDocumentSpec sample,
            ChunkMetrics metrics
    ) {
    }
}
