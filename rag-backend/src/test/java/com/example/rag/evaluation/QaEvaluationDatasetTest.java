package com.example.rag.evaluation;

import com.example.rag.support.TestPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 中文问答评测数据集完整性测试。 */
class QaEvaluationDatasetTest {

    private static final Path DATASET_PATH = backendFile("work/evaluation/day20-qa-eval-cases.json");
    private static final List<String> SAMPLE_FILES = List.of(
            "work/samples/day20-cn-结算异常处理指南.md",
            "work/samples/day20-cn-对账常见问题.md",
            "work/samples/day20-cn-值班巡检清单.txt"
    );
    private static final Set<String> SUPPORTED_CATEGORIES = Set.of("FACT", "SUMMARY", "PROCESS", "NO_ANSWER");
    private static final Set<String> SUPPORTED_EXPECTATION_TYPES = Set.of("SHOULD_ANSWER", "SHOULD_REJECT");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluationDatasetShouldBeCompleteAndChineseFocused() throws IOException {
        // 这组校验用来保证评测数据仍然满足当前中文问答基线的假设。
        assertThat(Files.exists(DATASET_PATH)).isTrue();
        SAMPLE_FILES.forEach(sample -> assertThat(Files.exists(backendFile(sample))).isTrue());

        JsonNode root = objectMapper.readTree(Files.readString(DATASET_PATH));
        assertThat(root.path("kbCode").asText()).isEqualTo("day20-cn-kb");
        assertThat(root.path("language").asText()).isEqualTo("zh-CN");
        assertThat(root.path("topK").asInt()).isEqualTo(3);

        JsonNode cases = root.path("cases");
        assertThat(cases.isArray()).isTrue();
        assertThat(cases).hasSize(6);

        Set<String> seenCaseCodes = new HashSet<>();
        boolean containsNoAnswerCase = false;
        for (JsonNode caseNode : cases) {
            String caseCode = caseNode.path("caseCode").asText();
            String category = caseNode.path("category").asText();
            String question = caseNode.path("question").asText();
            String expectedDocument = caseNode.path("expectedDocument").asText();
            String expectationType = caseNode.path("expectationType").asText();

            assertThat(caseCode).startsWith("DAY20-");
            assertThat(seenCaseCodes.add(caseCode)).isTrue();
            assertThat(SUPPORTED_CATEGORIES).contains(category);
            assertThat(SUPPORTED_EXPECTATION_TYPES).contains(expectationType);
            assertThat(question).isNotBlank();
            assertThat(containsHanText(question)).isTrue();
            assertThat(containsAsciiLetters(question)).isFalse();

            JsonNode expectedKeywords = caseNode.path("expectedKeywords");
            assertThat(expectedKeywords.isArray()).isTrue();
            assertThat(expectedKeywords).isNotEmpty();
            for (JsonNode keywordNode : expectedKeywords) {
                String keyword = keywordNode.asText();
                assertThat(keyword).isNotBlank();
                assertThat(containsHanText(keyword)).isTrue();
            }

            if ("NO_ANSWER".equals(category)) {
                containsNoAnswerCase = true;
                assertThat(expectedDocument).isBlank();
                assertThat(expectationType).isEqualTo("SHOULD_REJECT");
            } else {
                assertThat(expectedDocument).isNotBlank();
                assertThat(containsHanText(expectedDocument)).isTrue();
                assertThat(expectationType).isEqualTo("SHOULD_ANSWER");
            }
        }

        assertThat(containsNoAnswerCase).isTrue();
    }

    /** 判断文本中是否至少包含一个汉字。 */
    private boolean containsHanText(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    /** 判断文本中是否包含英文字母，用于限制问题集语言漂移。 */
    private boolean containsAsciiLetters(String value) {
        return value != null && value.chars().anyMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'));
    }

    private static Path backendFile(String relativePath) {
        return TestPaths.backendFile(relativePath);
    }
}
