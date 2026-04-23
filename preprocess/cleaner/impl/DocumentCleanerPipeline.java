package KLTN.RAG_CHATBOT_BE.preprocess.cleaner.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import KLTN.RAG_CHATBOT_BE.preprocess.cleaner.DocumentCleaner;
import KLTN.RAG_CHATBOT_BE.preprocess.cleaner.PageTextFilter;
import KLTN.RAG_CHATBOT_BE.preprocess.model.CleaningContext;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedDocument;
import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;

@Service
public class DocumentCleanerPipeline implements DocumentCleaner {

    private final List<PageTextFilter> orderedFilters;

    public DocumentCleanerPipeline(List<PageTextFilter> filters) {
        this.orderedFilters = filters.stream()
                .sorted(Comparator.comparingInt(PageTextFilter::getOrder))
                .toList();
    }

    @Override
    public ParsedDocument clean(ParsedDocument input, CleaningContext context) {
        List<ParsedPage> cleanedPages = new ArrayList<>();

        for (ParsedPage page : input.pages()) {
            ParsedPage current = page;
            for (PageTextFilter filter : orderedFilters) {
                current = filter.apply(current, context);
            }
            cleanedPages.add(current);
        }

        return ParsedDocument.builder()
                .sourceName(input.sourceName())
                .sourceType(input.sourceType())
                .sourceSize(input.sourceSize())
                .pages(cleanedPages)
                .metadata(mergeMetadata(input.metadata(), context.metadata()))
                .build();
    }

    private Map<String, String> mergeMetadata(Map<String, String> base, Map<String, String> additional) {
        if (base == null || base.isEmpty()) {
            return additional;
        }
        if (additional == null || additional.isEmpty()) {
            return base;
        }

        java.util.HashMap<String, String> merged = new java.util.HashMap<>(base);
        merged.putAll(additional);
        return merged;
    }
}
