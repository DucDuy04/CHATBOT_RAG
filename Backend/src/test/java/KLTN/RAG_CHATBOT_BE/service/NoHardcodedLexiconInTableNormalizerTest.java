package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NoHardcodedLexiconInTableNormalizerTest {

    @Test
    void productionTablePipelineHasNoDiagnosticOrDomainStringLiterals() throws Exception {
        List<Path> files = List.of(
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/ChunkingService2.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/CellAwareTableRowScorer.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/KeywordSearchService.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/TableIngestMetrics.java")
        );

        List<String> forbidden = List.of(
                "sotayhocvu", "knm1013", "ktr3185",
                "k45", "k46", "k47", "k48",
                "ky nang mem", "nhom", "giang vien", "phong", "thu",
                "tiet", "tiet hoc", "tin chi", "hoc ky",
                "ma hoc phan", "ten hoc phan", "ten lop hoc phan", "so tc",
                "ngay bat dau", "ghi chu"
        );

        for (Path file : files) {
            String source = Files.readString(file);
            List<String> literals = stringLiterals(source).stream()
                    .map(NoHardcodedLexiconInTableNormalizerTest::normalizeLiteral)
                    .toList();
            assertThat(literals)
                    .as("string literals in " + file)
                    .doesNotContainAnyElementsOf(forbidden);
        }
    }

    private static List<String> stringLiterals(String source) {
        Matcher m = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(source);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String normalizeLiteral(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
