package KLTN.RAG_CHATBOT_BE.rag.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatServiceAnswerStyleTest {

    @Test
    void stripLeadingSourcePreamble_removesTheoSourceComma() {
        String raw = "Theo Source 12, thời gian học quân sự là từ ngày 30/03/2026 đến ngày 26/04/2026.";
        assertEquals(
                "thời gian học quân sự là từ ngày 30/03/2026 đến ngày 26/04/2026.",
                ChatService.stripLeadingSourcePreamble(raw));
    }

    @Test
    void stripLeadingSourcePreamble_removesTheoNguonComma() {
        String raw = "Theo nguồn 14, Từ ngày 09/02/2026 đến ngày 01/03/2026.";
        assertEquals(
                "Từ ngày 09/02/2026 đến ngày 01/03/2026.",
                ChatService.stripLeadingSourcePreamble(raw));
    }

    @Test
    void stripLeadingSourcePreamble_removesDuaTrenSource() {
        String raw = "Dựa trên Source 3, kết quả là 42.";
        assertEquals("kết quả là 42.", ChatService.stripLeadingSourcePreamble(raw));
    }

    @Test
    void stripLeadingSourcePreamble_removesDuaVaoNguon() {
        String raw = "Dựa vào nguồn 5: thông tin đã có.";
        assertEquals("thông tin đã có.", ChatService.stripLeadingSourcePreamble(raw));
    }

    @Test
    void stripLeadingSourcePreamble_leavesDirectAnswerUntouched() {
        String direct = "Thời gian học quân sự là từ ngày 30/03/2026 đến ngày 26/04/2026.";
        assertEquals(direct, ChatService.stripLeadingSourcePreamble(direct));
    }

    @Test
    void stripLeadingSourcePreamble_nullAndBlank() {
        assertNull(ChatService.stripLeadingSourcePreamble(null));
        assertEquals("  ", ChatService.stripLeadingSourcePreamble("  "));
    }
}
