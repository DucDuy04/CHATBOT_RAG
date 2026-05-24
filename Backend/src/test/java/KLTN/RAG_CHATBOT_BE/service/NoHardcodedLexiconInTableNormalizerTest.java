package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/CellAwareTableRowScorer.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/KeywordSearchService.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java"),
                Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/service/TableHeaderDisplayCleaner.java")
        );

        for (Path file : files) {
            String source = Files.readString(file);
            List<String> literals = stringLiterals(source);
            assertThat(literals)
                    .as("string literals in " + file)
                    .doesNotContain(
                            "sotayhocvu", "kỹ năng mềm", "ky nang mem", "knm1013", "ktr3185",
                            "k45", "k46", "k47", "k48",
                            "nhóm", "nhom", "giảng viên", "giang vien", "phòng", "phong",
                            "thứ", "thu", "tiết", "tiet", "tín chỉ", "tin chi",
                            "học kỳ", "hoc ky", "mã học phần", "ma hoc phan",
                            "tên học phần", "ten hoc phan");
        }
    }

    private static List<String> stringLiterals(String source) {
        Matcher m = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(source);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(m.group(1).toLowerCase());
        }
        return out;
    }
}
