package KLTN.RAG_CHATBOT_BE.preprocess.cleaner;

import KLTN.RAG_CHATBOT_BE.preprocess.model.CleaningContext;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;

public interface PageTextFilter {

    int getOrder();

    ParsedPage apply(ParsedPage page, CleaningContext context);
}
