package KLTN.RAG_CHATBOT_BE;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full-stack smoke test — requires live MySQL, Qdrant, and API keys.
 * Default {@code mvn test} excludes {@code integration} tag (see surefire config).
 */
@SpringBootTest
@Tag("integration")
@Disabled("Requires full integration stack (MySQL + Qdrant + env). Run manually with -Dgroups=integration when stack is up.")
class RagChatbotBeApplicationTests {

    @Test
    void contextLoads() {
    }
}
