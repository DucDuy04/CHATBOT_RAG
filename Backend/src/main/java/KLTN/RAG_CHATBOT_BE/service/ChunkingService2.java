package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.record.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.record.Section;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkingService2 {

    private static final int MAX_CHARS_PER_TEXT_CHUNK = 2200;
    private static final int OVERLAP_CHARS = 250;
    private static final int TABLE_ROWS_PER_GROUP = 10;

    // Trích xuất số heading từ đầu header (vd: "10.1 Tên mục" → "10.1")
    private static final Pattern SECTION_NUMBER_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)[.\\s]");

    public List<DocumentChunk> processSections2(List<Section> sections) {
        List<DocumentChunk> finalChunks = new ArrayList<>();

        if (sections == null || sections.isEmpty()) {
            return finalChunks;
        }

        int globalOrder = 0;

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            Section section = sections.get(sectionIndex);

            // Ưu tiên dùng số heading thực tế (vd "10.1") để sectionId mang nghĩa cấu trúc.
            // Fallback về chỉ số nếu header không có số heading.
            String sectionNumber = extractSectionNumber(section.header());
            String sectionId = sectionNumber != null
                    ? "sec_" + sectionNumber
                    : "sec_idx_" + sectionIndex;
            String parentId = sectionNumber != null
                    ? "parent_" + extractParentSectionNumber(sectionNumber)
                    : "parent_idx_" + sectionIndex;
            String headingPathText = safeText(section.header(), "Untitled Section");

            List<String> segments = splitByTableBlocks(section.content());

            for (String segment : segments) {
                if (segment.startsWith("[TABLE_START]")) {
                    String tableContent = segment
                            .replace("[TABLE_START]", "")
                            .replace("[TABLE_END]", "")
                            .trim();

                    if (tableContent.isBlank()) {
                        continue;
                    }

                    String tableId = "tbl_" + sectionIndex + "_" + globalOrder;

                    String summary = buildTableSummary(section.header(), tableContent);

                    finalChunks.add(createEnrichedChunk(
                            summary,
                            section,
                            "table_summary",
                            sectionId,
                            parentId,
                            tableId,
                            headingPathText,
                            globalOrder++
                    ));

                    for (String tableGroup : splitMarkdownTableRows(tableContent, TABLE_ROWS_PER_GROUP)) {
                        finalChunks.add(createEnrichedChunk(
                                tableGroup,
                                section,
                                "table_row_group",
                                sectionId,
                                parentId,
                                tableId,
                                headingPathText,
                                globalOrder++
                        ));
                    }
                } else {
                    for (String textChunk : chunkPlainText(segment)) {
                        finalChunks.add(createEnrichedChunk(
                                textChunk,
                                section,
                                "text",
                                sectionId,
                                parentId,
                                null,
                                headingPathText,
                                globalOrder++
                        ));
                    }
                }
            }
        }

        return finalChunks;
    }

    private List<String> splitByTableBlocks(String content) {
        List<String> result = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return result;
        }

        if (content.contains("[TABLE_START]")) {
            return splitByExplicitTableMarkers(content);
        }

        String[] lines = content.split("\\R");
        StringBuilder normalText = new StringBuilder();
        StringBuilder tableText = new StringBuilder();
        boolean inTable = false;

        for (String line : lines) {
            String trimmed = line.trim();
            boolean tableLine = trimmed.startsWith("|") && trimmed.endsWith("|");

            if (tableLine) {
                if (!inTable) {
                    if (normalText.length() > 0) {
                        result.add(normalText.toString().trim());
                        normalText.setLength(0);
                    }
                    inTable = true;
                }

                tableText.append(line).append("\n");
            } else {
                if (inTable) {
                    result.add("[TABLE_START]\n" + tableText.toString().trim() + "\n[TABLE_END]");
                    tableText.setLength(0);
                    inTable = false;
                }

                normalText.append(line).append("\n");
            }
        }

        if (inTable && tableText.length() > 0) {
            result.add("[TABLE_START]\n" + tableText.toString().trim() + "\n[TABLE_END]");
        }

        if (normalText.length() > 0) {
            result.add(normalText.toString().trim());
        }

        return result;
    }

    private List<String> splitByExplicitTableMarkers(String content) {
        List<String> result = new ArrayList<>();
        int searchFrom = 0;

        while (searchFrom < content.length()) {
            int tableStart = content.indexOf("[TABLE_START]", searchFrom);
            if (tableStart < 0) {
                addIfNotBlank(result, content.substring(searchFrom));
                break;
            }

            addIfNotBlank(result, content.substring(searchFrom, tableStart));

            int tableContentStart = tableStart + "[TABLE_START]".length();
            int tableEnd = content.indexOf("[TABLE_END]", tableContentStart);
            if (tableEnd < 0) {
                addIfNotBlank(result, content.substring(tableContentStart));
                break;
            }

            String tableContent = content.substring(tableContentStart, tableEnd).trim();
            if (!tableContent.isBlank()) {
                result.add("[TABLE_START]\n" + tableContent + "\n[TABLE_END]");
            }

            searchFrom = tableEnd + "[TABLE_END]".length();
        }

        return result;
    }

    private void addIfNotBlank(List<String> result, String value) {
        if (value == null) {
            return;
        }

        String normalized = value.trim();
        if (!normalized.isBlank()) {
            result.add(normalized);
        }
    }

    private List<String> chunkPlainText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String normalized = text.trim();

        if (normalized.length() <= MAX_CHARS_PER_TEXT_CHUNK) {
            chunks.add(normalized);
            return chunks;
        }

        int start = 0;

        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHARS_PER_TEXT_CHUNK, normalized.length());

            if (end < normalized.length()) {
                int sentenceBoundary = normalized.lastIndexOf(".", end);
                int newlineBoundary = normalized.lastIndexOf("\n", end);
                int boundary = Math.max(sentenceBoundary, newlineBoundary);

                if (boundary > start + 500) {
                    end = boundary + 1;
                }
            }

            String chunk = normalized.substring(start, end).trim();

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= normalized.length()) {
                break;
            }

            start = Math.max(0, end - OVERLAP_CHARS);
        }

        return chunks;
    }

    private DocumentChunk createEnrichedChunk(
            String rawContent,
            Section section,
            String chunkType,
            String sectionId,
            String parentId,
            String tableId,
            String headingPathText,
            int orderIndex
    ) {
        String safeRawContent = safeText(rawContent, "");
        String safeHeading = safeText(headingPathText, safeText(section.header(), "Untitled Section"));

        String enrichedContent = String.format(
                "Document Section: %s%nType: %s%nPages: %d-%d%n%nContent:%n%s",
                safeHeading,
                chunkType,
                section.startPage(),
                section.endPage(),
                safeRawContent
        );

        int tokenEstimate = Math.max(1, enrichedContent.length() / 4);

        return new DocumentChunk(
                enrichedContent,
                safeText(section.header(), "Untitled Section"),
                section.startPage(),
                section.endPage(),
                chunkType,
                sectionId,
                parentId,
                tableId,
                safeHeading,
                orderIndex,
                tokenEstimate
        );
    }

    private String buildTableSummary(String sectionTitle, String tableMarkdown) {
        String firstLine = tableMarkdown.lines()
                .filter(line -> line.trim().startsWith("|"))
                .findFirst()
                .orElse("");

        return """
                Bảng trong section "%s".
                Các cột: %s
                Bảng này cần được dùng khi câu hỏi yêu cầu liệt kê, tra cứu bảng, danh sách hoặc thông tin theo hàng/cột.
                """.formatted(safeText(sectionTitle, "Untitled Section"), firstLine);
    }

    private List<String> splitMarkdownTableRows(String tableMarkdown, int rowsPerGroup) {
        List<String> lines = tableMarkdown.lines()
                .filter(line -> !line.isBlank())
                .toList();

        if (lines.size() <= rowsPerGroup + 2) {
            return List.of(tableMarkdown);
        }

        String header = lines.get(0) + "\n" + lines.get(1);
        List<String> groups = new ArrayList<>();

        for (int i = 2; i < lines.size(); i += rowsPerGroup) {
            int end = Math.min(i + rowsPerGroup, lines.size());
            String body = String.join("\n", lines.subList(i, end));
            groups.add(header + "\n" + body);
        }

        return groups;
    }

    private String safeText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Trích xuất số section từ header (vd: "10.1 Tên mục" → "10.1", "General" → null).
     */
    private String extractSectionNumber(String header) {
        if (header == null || header.isBlank()) return null;
        Matcher m = SECTION_NUMBER_PATTERN.matcher(header.trim());
        return m.find() ? m.group(1) : null;
    }

    /**
     * Trích xuất section cha (vd: "10.1" → "10", "3.2.1" → "3.2", "10" → "10").
     */
    private String extractParentSectionNumber(String sectionNumber) {
        if (sectionNumber == null || sectionNumber.isBlank()) return "root";
        int lastDot = sectionNumber.lastIndexOf('.');
        return lastDot > 0 ? sectionNumber.substring(0, lastDot) : sectionNumber;
    }
}
