package KLTN.RAG_CHATBOT_BE.preprocess.cleaner.impl;

import KLTN.RAG_CHATBOT_BE.preprocess.cleaner.PageTextFilter;
import KLTN.RAG_CHATBOT_BE.preprocess.config.CleaningProperties;
import KLTN.RAG_CHATBOT_BE.preprocess.model.CleaningContext;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class NoisePatternFilter implements PageTextFilter {

    private final CleaningProperties properties;

    public NoisePatternFilter(CleaningProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public ParsedPage apply(ParsedPage page, CleaningContext context) {
        if (!properties.isEnableNoiseRemoval() || page.rawText() == null || page.rawText().isBlank()) {
            return page;
        }

        List<Pattern> patterns = properties.getNoiseRegex().stream()
                .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
                .toList();

        String[] lines = page.rawText().split("\\R");
        List<String> kept = new ArrayList<>(lines.length);

        for (String line : lines) {
            String normalizedLine = line == null ? "" : line.trim();
            if (normalizedLine.isBlank()) {
                kept.add("");
                continue;
            }

            boolean isNoise = patterns.stream().anyMatch(pattern -> pattern.matcher(normalizedLine).matches());
            if (!isNoise) {
                kept.add(line);
            }
        }

        String cleaned = String.join("\n", kept);
        return ParsedPage.builder()
                .pageNumber(page.pageNumber())
                .rawText(page.rawText())
                .cleanedText(cleaned)
                .tableBlocks(page.tableBlocks())
                .metadata(page.metadata())
                .build();
    }
}
