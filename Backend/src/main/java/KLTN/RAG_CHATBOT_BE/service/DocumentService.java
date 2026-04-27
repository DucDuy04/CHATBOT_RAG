package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSection;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSectionRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentTable;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentTableRepository;
import KLTN.RAG_CHATBOT_BE.domain.enums.DocumentStatus;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.record.Section;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final WidgetConfigRepository widgetConfigRepository;

    private final DocumentParserService documentParserService;
    private final ChunkingService2 chunkingService2;
    private final EmbeddingService embeddingService;

    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentSectionRepository documentSectionRepository;
    private final DocumentTableRepository documentTableRepository;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public Document uploadAndProcess(MultipartFile file, UUID widgetId) throws IOException {
        WidgetConfig widgetConfig = widgetConfigRepository.findById(widgetId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Widget với ID: " + widgetId));

        String savedPath = saveFile(file);

        Document document = Document.builder()
                .widgetConfig(widgetConfig)
                .fileName(file.getOriginalFilename())
                .filePath(savedPath)
                .fileType(getFileType(file.getOriginalFilename()))
                .fileSize(file.getSize())
                .status(DocumentStatus.PENDING)
                .build();

        document = documentRepository.save(document);

        try {
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            List<Section> sections = documentParserService.parse(file);
            List<KLTN.RAG_CHATBOT_BE.record.DocumentChunk> chunks =
                    chunkingService2.processSections2(sections);

            log.info(
                    "Parse document={} được {} sections, chunk được {} chunks",
                    document.getFileName(),
                    sections.size(),
                    chunks.size()
            );

            Map<String, DocumentSection> sectionMap = saveSections(
                    sections,
                    document,
                    widgetConfig
            );

            Map<String, DocumentTable> tableMap = saveTables(
                    chunks,
                    sectionMap,
                    document,
                    widgetConfig
            );

            List<KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk> savedChunks =
                    saveChunks(
                            chunks,
                            sectionMap,
                            tableMap,
                            document,
                            widgetConfig
                    );

            linkPrevNextChunks(savedChunks);

            embeddingService.embedAndStore(
                    savedChunks,
                    document.getId(),
                    document.getFileName(),
                    widgetId
            );

            document.setStatus(DocumentStatus.COMPLETED);
            document.setChunkCount(savedChunks.size());
            documentRepository.save(document);

            log.info(
                    "Xử lý xong document={}, sections={}, tables={}, chunks={}",
                    document.getFileName(),
                    sectionMap.size(),
                    tableMap.size(),
                    savedChunks.size()
            );

        } catch (Exception e) {
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);

            log.error(
                    "Lỗi xử lý document={}, error={}",
                    document.getFileName(),
                    e.getMessage(),
                    e
            );

            throw e;
        }

        return document;
    }

    private Map<String, DocumentSection> saveSections(
            List<Section> sections,
            Document document,
            WidgetConfig widgetConfig
    ) {
        Map<String, DocumentSection> sectionMap = new LinkedHashMap<>();
        List<HeadingNode> headingStack = new java.util.ArrayList<>();
        Map<String, Integer> keyCounts = new java.util.HashMap<>();

        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);

            String title = safeText(section.header(), "Untitled Section");
            String sectionNumber = documentParserService.extractSectionNumber(title);
            String baseKey = sectionNumber != null
                    ? "sec_" + sectionNumber
                    : "sec_idx_" + i;
            int seen = keyCounts.getOrDefault(baseKey, 0);
            keyCounts.put(baseKey, seen + 1);
            String sectionKey = seen == 0 ? baseKey : (baseKey + "__dup" + (seen + 1));
            String parentSectionKey = sectionNumber != null
                    ? "parent_" + extractParentSectionNumber(sectionNumber)
                    : "parent_idx_" + i;
            String headingPathText = buildHeadingPathText(headingStack, title, sectionNumber);

            DocumentSection sectionEntity = DocumentSection.builder()
                    .document(document)
                    .widgetConfig(widgetConfig)
                    .sectionKey(sectionKey)
                    .parentSectionKey(parentSectionKey)
                    .title(title)
                    .headingPathText(headingPathText)
                    .pageStart(section.startPage())
                    .pageEnd(section.endPage())
                    .orderIndex(i)
                    .build();

            sectionEntity = documentSectionRepository.save(sectionEntity);
            sectionMap.put(sectionKey, sectionEntity);
        }

        return sectionMap;
    }

    private String extractParentSectionNumber(String sectionNumber) {
        if (sectionNumber == null || sectionNumber.isBlank()) return "root";
        int lastDot = sectionNumber.lastIndexOf('.');
        return lastDot > 0 ? sectionNumber.substring(0, lastDot) : sectionNumber;
    }

    private String buildHeadingPathText(List<HeadingNode> stack, String header, String sectionNumber) {
        String safeHeader = safeText(header, "Untitled Section");
        if (sectionNumber == null || sectionNumber.isBlank()) {
            return safeHeader;
        }

        int level = sectionNumber.split("\\.").length;
        String titleOnly = extractTitleOnly(safeHeader, sectionNumber);

        while (stack.size() >= level) {
            stack.remove(stack.size() - 1);
        }
        stack.add(new HeadingNode(sectionNumber, titleOnly));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            HeadingNode node = stack.get(i);
            if (i > 0) sb.append(" > ");
            sb.append(node.number()).append(" ").append(node.title());
        }
        return sb.toString();
    }

    private String extractTitleOnly(String header, String sectionNumber) {
        if (header == null) return "";
        String h = header.trim();
        if (h.startsWith(sectionNumber)) {
            h = h.substring(sectionNumber.length()).trim();
        }
        if (h.startsWith(".")) {
            h = h.substring(1).trim();
        }
        return safeText(h, header).trim();
    }

    private record HeadingNode(String number, String title) {}

    private Map<String, DocumentTable> saveTables(
            List<KLTN.RAG_CHATBOT_BE.record.DocumentChunk> chunks,
            Map<String, DocumentSection> sectionMap,
            Document document,
            WidgetConfig widgetConfig
    ) {
        Map<String, DocumentTable> tableMap = new LinkedHashMap<>();

        for (KLTN.RAG_CHATBOT_BE.record.DocumentChunk chunk : chunks) {
            if (chunk.tableId() == null || chunk.tableId().isBlank()) {
                continue;
            }

            if (tableMap.containsKey(chunk.tableId())) {
                continue;
            }

            DocumentSection sectionEntity = sectionMap.get(chunk.sectionId());

            DocumentTable tableEntity = DocumentTable.builder()
                    .document(document)
                    .widgetConfig(widgetConfig)
                    .section(sectionEntity)
                    .tableKey(chunk.tableId())
                    .sectionKey(chunk.sectionId())
                    .title(safeText(chunk.header(), "Table"))
                    .pageStart(chunk.startPage())
                    .pageEnd(chunk.endPage())
                    .orderIndex(chunk.orderIndex())
                    .markdownContent(chunk.content())
                    .jsonContent(null)
                    .build();

            tableEntity = documentTableRepository.save(tableEntity);
            tableMap.put(chunk.tableId(), tableEntity);
        }

        return tableMap;
    }

    private List<KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk> saveChunks(
            List<KLTN.RAG_CHATBOT_BE.record.DocumentChunk> chunks,
            Map<String, DocumentSection> sectionMap,
            Map<String, DocumentTable> tableMap,
            Document document,
            WidgetConfig widgetConfig
    ) {
        List<KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk> entities =
                new java.util.ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            KLTN.RAG_CHATBOT_BE.record.DocumentChunk chunk = chunks.get(i);

            DocumentSection sectionEntity = sectionMap.get(chunk.sectionId());
            DocumentTable tableEntity = chunk.tableId() == null
                    ? null
                    : tableMap.get(chunk.tableId());

            KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk chunkEntity =
                    KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.builder()
                            .document(document)
                            .widgetConfig(widgetConfig)
                            .section(sectionEntity)
                            .table(tableEntity)
                            .chunkIndex(i)
                            .content(chunk.content())
                            .chunkType(safeText(chunk.chunkType(), "text"))
                            .sectionId(chunk.sectionId())
                            .parentId(chunk.parentId())
                            .tableId(chunk.tableId())
                            .sectionTitle(chunk.header())
                            .headingPathText(chunk.headingPathText())
                            .pageStart(chunk.startPage())
                            .pageEnd(chunk.endPage())
                            .orderIndex(chunk.orderIndex())
                            .tokenCount(chunk.tokenCount())
                            .sourceFile(document.getFileName())
                            .build();

            entities.add(chunkEntity);
        }

        return documentChunkRepository.saveAll(entities);
    }

    private void linkPrevNextChunks(
            List<KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk> savedChunks
    ) {
        if (savedChunks == null || savedChunks.isEmpty()) {
            return;
        }

        for (int i = 0; i < savedChunks.size(); i++) {
            KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk current = savedChunks.get(i);

            if (i > 0) {
                current.setPrevChunk(savedChunks.get(i - 1));
            }

            if (i < savedChunks.size() - 1) {
                current.setNextChunk(savedChunks.get(i + 1));
            }
        }

        documentChunkRepository.saveAll(savedChunks);
    }

    public List<Document> getDocumentsByWidget(UUID widgetId) {
        return documentRepository.findByWidgetConfigId(widgetId);
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    private String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFileName = file.getOriginalFilename();
        String safeFileName = originalFileName == null || originalFileName.isBlank()
                ? "uploaded_file"
                : originalFileName.replaceAll("[\\\\/:*?\"<>|]", "_");

        String fileName = System.currentTimeMillis() + "_" + safeFileName;
        Path filePath = uploadPath.resolve(fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return filePath.toString();
    }

    private String getFileType(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "UNKNOWN";
        }

        String lower = fileName.toLowerCase();

        if (lower.endsWith(".pdf")) {
            return "PDF";
        }

        if (lower.endsWith(".txt")) {
            return "TXT";
        }

        if (lower.endsWith(".docx")) {
            return "DOCX";
        }

        if (lower.endsWith(".doc")) {
            return "DOC";
        }

        return "UNKNOWN";
    }

    private String safeText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}