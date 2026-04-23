package KLTN.RAG_CHATBOT_BE.preprocess.chunk.strategy;

import KLTN.RAG_CHATBOT_BE.preprocess.chunk.model.ChunkCandidate;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedDocument;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SemanticAwareChunkingStrategy implements ChunkingStrategy {

    private static final int MAX_CHARS = 1200;

    @Override
    public List<ChunkCandidate> chunk(ParsedDocument document) {
        List<ChunkCandidate> out = new ArrayList<>();
        if (document.pages() == null || document.pages().isEmpty()) {
            return out;
        }

        int chunkIndex = 0;
        for (ParsedPage page : document.pages()) {
            String content = page.cleanedText() != null ? page.cleanedText() : page.rawText();
            if (content == null || content.isBlank()) {
                continue;
            }

            List<String> blocks = splitSemanticBlocks(content);
            for (String block : blocks) {
                if (block.length() <= MAX_CHARS) {
                    out.add(toChunk(block, chunkIndex++, page.pageNumber(), detectSection(block)));
                } else {
                    int start = 0;
                    while (start < block.length()) {
                        int end = Math.min(start + MAX_CHARS, block.length());
                        String part = block.substring(start, end).trim();
                        if (!part.isBlank()) {
                            out.add(toChunk(part, chunkIndex++, page.pageNumber(), detectSection(part)));
                        }
                        start = end;
                    }
                }
            }
        }

        return out;
    }

    private List<String> splitSemanticBlocks(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks = normalized.split("\\n\\s*\\n+");

        List<String> out = new ArrayList<>();
        for (String block : blocks) {
            String trimmed = block.trim();
            if (!trimmed.isBlank()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private ChunkCandidate toChunk(String content, int chunkIndex, Integer pageNumber, String section) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("strategy", "semantic-aware-v1");

        return new ChunkCandidate(
            content,
            chunkIndex,
            pageNumber,
            section,
            metadata
        );
    }

    private String detectSection(String content) {
        String firstLine = content.lines().findFirst().orElse("").trim();
        if (firstLine.matches("^[A-Z0-9][A-Z0-9\\s:.-]{2,}$")) {
            return firstLine;
        }
        if (content.startsWith("[TABLE_START]")) {
            return "TABLE";
        }
        return "BODY";
    }
}
