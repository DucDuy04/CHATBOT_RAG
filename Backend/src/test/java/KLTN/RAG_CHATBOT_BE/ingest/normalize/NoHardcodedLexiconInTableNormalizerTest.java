package KLTN.RAG_CHATBOT_BE.ingest.normalize;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Ensures domain-specific literals stay in tests/fixtures only — not production ingest/normalize code.
 */
class NoHardcodedLexiconInTableNormalizerTest {

    private static final List<String> BANNED_LITERALS = List.of(
            "LUA1012",
            "TIN1093",
            "KNM1013",
            "KTR3185",
            "K45",
            "K46",
            "Kiến trúc",
            "Công nghệ sinh học",
            "Nguyễn Thị Vân Anh",
            "Nguyễn Thị Thanh Nhàn"
    );

    private static final List<Path> PRODUCTION_FILES = List.of(
            Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/ingest/normalize/NormalizedTableService.java"),
            Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/ingest/parser/DocumentParserService.java"),
            Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/ingest/chunking/ChunkingService2.java"),
            Path.of("src/main/java/KLTN/RAG_CHATBOT_BE/rag/retrieve/CellAwareTableRowScorer.java")
    );

    @Test
    void normalizedTableService_hasNoBannedDomainLiterals() throws Exception {
        assertNoBannedLiterals(PRODUCTION_FILES.get(0));
    }

    @Test
    void documentParserService_hasNoBannedDomainLiterals() throws Exception {
        assertNoBannedLiterals(PRODUCTION_FILES.get(1));
    }

    @Test
    void chunkingService_hasNoBannedDomainLiterals() throws Exception {
        assertNoBannedLiterals(PRODUCTION_FILES.get(2));
    }

    @Test
    void cellAwareScorer_hasNoBannedDomainLiterals() throws Exception {
        assertNoBannedLiterals(PRODUCTION_FILES.get(3));
    }

    private static void assertNoBannedLiterals(Path relativePath) throws Exception {
        String source = Files.readString(relativePath);
        for (String banned : BANNED_LITERALS) {
            assertFalse(
                    source.contains(banned),
                    () -> relativePath + " must not contain hardcoded domain literal: " + banned);
        }
    }
}
