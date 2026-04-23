package KLTN.RAG_CHATBOT_BE.preprocess.cleaner.impl;

import KLTN.RAG_CHATBOT_BE.preprocess.cleaner.PageTextFilter;
import KLTN.RAG_CHATBOT_BE.preprocess.config.CleaningProperties;
import KLTN.RAG_CHATBOT_BE.preprocess.model.CleaningContext;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;
import org.springframework.stereotype.Component;

@Component
public class LineUnbreakFilter implements PageTextFilter {

    private final CleaningProperties properties;

    public LineUnbreakFilter(CleaningProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public ParsedPage apply(ParsedPage page, CleaningContext context) {
        if (!properties.isEnableLineUnbreak()) {
            return page;
        }

        String source = page.cleanedText() != null ? page.cleanedText() : page.rawText();
        if (source == null || source.isBlank()) {
            return page;
        }

        String[] lines = source.split("\\R");
        StringBuilder out = new StringBuilder();

        for (String line : lines) {
            String current = line == null ? "" : line.trim();
            if (current.isBlank()) {
                out.append("\n");
                continue;
            }

            if (out.length() > 0 && shouldJoin(out, current)) {
                out.append(properties.getLineJoinSeparator());
            } else if (out.length() > 0) {
                out.append("\n");
            }
            out.append(current);
        }

        return ParsedPage.builder()
                .pageNumber(page.pageNumber())
                .rawText(page.rawText())
                .cleanedText(out.toString())
                .tableBlocks(page.tableBlocks())
                .metadata(page.metadata())
                .build();
    }

    private boolean shouldJoin(StringBuilder out, String nextLine) {
        int i = out.length() - 1;
        while (i >= 0 && Character.isWhitespace(out.charAt(i))) {
            i--;
        }
        if (i < 0) {
            return false;
        }

        char last = out.charAt(i);
        if (last == '.' || last == '!' || last == '?' || last == ':' || last == ';') {
            return false;
        }

        char first = nextLine.charAt(0);
        if (Character.isDigit(first)) {
            return false;
        }

        return Character.isLowerCase(last) && Character.isLowerCase(first);
    }
}
