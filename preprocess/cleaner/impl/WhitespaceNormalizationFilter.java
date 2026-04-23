package KLTN.RAG_CHATBOT_BE.preprocess.cleaner.impl;

import KLTN.RAG_CHATBOT_BE.preprocess.cleaner.PageTextFilter;
import KLTN.RAG_CHATBOT_BE.preprocess.config.CleaningProperties;
import KLTN.RAG_CHATBOT_BE.preprocess.model.CleaningContext;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;
import org.springframework.stereotype.Component;

@Component
public class WhitespaceNormalizationFilter implements PageTextFilter {

    private final CleaningProperties properties;

    public WhitespaceNormalizationFilter(CleaningProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public ParsedPage apply(ParsedPage page, CleaningContext context) {
        if (!properties.isEnableWhitespaceNormalization()) {
            return page;
        }

        String source = page.cleanedText() != null ? page.cleanedText() : page.rawText();
        if (source == null) {
            source = "";
        }

        String cleaned = source
                .replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ')
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();

        return ParsedPage.builder()
                .pageNumber(page.pageNumber())
                .rawText(page.rawText())
                .cleanedText(cleaned)
                .tableBlocks(page.tableBlocks())
                .metadata(page.metadata())
                .build();
    }
}
