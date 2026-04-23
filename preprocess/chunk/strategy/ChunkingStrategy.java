package KLTN.RAG_CHATBOT_BE.preprocess.chunk.strategy;

import KLTN.RAG_CHATBOT_BE.preprocess.chunk.model.ChunkCandidate;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedDocument;

import java.util.List;

public interface ChunkingStrategy {

    List<ChunkCandidate> chunk(ParsedDocument document);
}
