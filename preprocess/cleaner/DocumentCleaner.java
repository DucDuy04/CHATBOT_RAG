package KLTN.RAG_CHATBOT_BE.preprocess.cleaner;

import KLTN.RAG_CHATBOT_BE.preprocess.model.CleaningContext;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedDocument;

public interface DocumentCleaner {

    ParsedDocument clean(ParsedDocument input, CleaningContext context);
}
