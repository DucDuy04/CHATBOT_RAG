package KLTN.RAG_CHATBOT_BE.index.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for 24D4: Vietnamese Unicode in Qdrant REST payload must not mojibake via Jackson JSON.
 */
class QdrantPayloadUnicodeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void buildQdrantRestPayload_preservesVietnameseKeysAndValues() throws Exception {
        String cellsJson = "{\"Mã học phần\":\"LUA1012\",\"Tên lớp học phần\":\"Pháp luật Việt Nam đại cương - Nhóm 1\"}";
        Metadata metadata = new Metadata()
                .put("chunk_type", "normalized_table_row")
                .put("cells_json", cellsJson)
                .put("group_context", "Ngành: Công nghệ thông tin - Khóa K46")
                .put("section_title", "Thời khóa biểu");

        TextSegment segment = TextSegment.from("Nội dung dòng bảng có dấu tiếng Việt", metadata);

        Map<String, Object> payload = EmbeddingService.buildQdrantRestPayload(segment);

        assertEquals("Nội dung dòng bảng có dấu tiếng Việt", payload.get("text_segment"));
        assertEquals(cellsJson, payload.get("cells_json"));
        assertEquals("Ngành: Công nghệ thông tin - Khóa K46", payload.get("group_context"));
        assertEquals("Thời khóa biểu", payload.get("section_title"));

        String serialized = JSON.writeValueAsString(Map.of("points", List.of(Map.of("payload", payload))));
        byte[] utf8 = serialized.getBytes(StandardCharsets.UTF_8);
        String roundTrip = new String(utf8, StandardCharsets.UTF_8);

        assertTrue(roundTrip.contains("Pháp luật Việt Nam"));
        assertTrue(roundTrip.contains("Mã học phần"));
        assertFalse(roundTrip.contains("PhÃ¡p"));
        assertFalse(roundTrip.contains("MÃ£"));
    }

    @Test
    void buildQdrantRestPayload_cellsJsonRemainsValidJsonAfterSerialization() throws Exception {
        String cellsJson = "{\"Giảng viên\":\"Nguyễn Thị Vân Anh\"}";
        Metadata rowMeta = new Metadata()
                .put("cells_json", cellsJson)
                .put("chunk_type", "normalized_table_row");
        TextSegment segment = TextSegment.from("row", rowMeta);

        Map<String, Object> payload = EmbeddingService.buildQdrantRestPayload(segment);
        String body = JSON.writeValueAsString(payload);

        assertTrue(body.contains("Nguyễn Thị Vân Anh"));
        assertEquals(cellsJson, payload.get("cells_json"));
    }
}
