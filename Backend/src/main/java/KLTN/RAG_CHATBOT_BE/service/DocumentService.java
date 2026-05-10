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
import KLTN.RAG_CHATBOT_BE.dto.DocumentChunkResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentPageResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentStatusResponse;
import KLTN.RAG_CHATBOT_BE.record.Section;
import KLTN.RAG_CHATBOT_BE.support.BytesMultipartFile;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
                .mimeType(file.getContentType())
                .status(DocumentStatus.PENDING)
                .build();

        document = documentRepository.save(document);

        try {
            return executeProcessing(document, file);
        } catch (Exception e) {
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            log.error(
                    "Lỗi xử lý document={}, error={}",
                    document.getFileName(),
                    e.getMessage(),
                    e
            );
            if (e instanceof IOException io) {
                throw io;
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Pipeline parse → chunk → embed (giữ nguyên logic cốt lõi).
     */
    private Document executeProcessing(Document document, MultipartFile file) throws Exception {
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

        UUID widgetId = document.getWidgetConfig().getId();

        Map<String, DocumentSection> sectionMap = saveSections(
                sections,
                document,
                document.getWidgetConfig()
        );

        Map<String, DocumentTable> tableMap = saveTables(
                chunks,
                sectionMap,
                document,
                document.getWidgetConfig()
        );

        List<KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk> savedChunks =
                saveChunks(
                        chunks,
                        sectionMap,
                        tableMap,
                        document,
                        document.getWidgetConfig()
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

        return document;
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse listDocuments(
            String search,
            String type,
            String chatbotIdStr,
            String statusFe,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Specification<Document> spec = buildDocumentSpec(search, type, chatbotIdStr, statusFe);
        Page<Document> result = documentRepository.findAll(spec, pageable);
        List<DocumentResponse> items = result.getContent().stream()
                .map(this::toDocumentResponse)
                .toList();
        return DocumentPageResponse.builder()
                .items(items)
                .page(result.getNumber())
                .size(result.getSize())
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private Specification<Document> buildDocumentSpec(
            String search,
            String type,
            String chatbotIdStr,
            String statusFe
    ) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            Join<Object, Object> widgetJoin = root.join("widgetConfig", JoinType.INNER);

            if (search != null && !search.isBlank()) {
                preds.add(cb.like(cb.lower(root.get("fileName")), "%" + search.trim().toLowerCase() + "%"));
            }
            if (type != null && !type.isBlank()) {
                preds.add(cb.equal(cb.upper(root.get("fileType")), type.trim().toUpperCase()));
            }
            if (chatbotIdStr != null && !chatbotIdStr.isBlank()) {
                try {
                    UUID wid = UUID.fromString(chatbotIdStr.trim());
                    preds.add(cb.equal(widgetJoin.get("id"), wid));
                } catch (IllegalArgumentException e) {
                    preds.add(cb.equal(cb.literal(1), cb.literal(0)));
                }
            }
            if (statusFe != null && !statusFe.isBlank()) {
                String s = statusFe.trim().toUpperCase();
                switch (s) {
                    case "INDEXED" -> preds.add(cb.equal(root.get("status"), DocumentStatus.COMPLETED));
                    case "PROCESSING" ->
                            preds.add(root.get("status").in(DocumentStatus.PENDING, DocumentStatus.PROCESSING));
                    case "FAILED" -> preds.add(cb.equal(root.get("status"), DocumentStatus.FAILED));
                    default -> {
                    }
                }
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getDocumentStatusDto(UUID id) {
        Document d = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return DocumentStatusResponse.builder()
                .id(d.getId())
                .status(toFeStatus(d.getStatus()))
                .progress(progressFor(d))
                .chunkCount(d.getChunkCount())
                .error(d.getStatus() == DocumentStatus.FAILED ? "Xử lý tài liệu thất bại." : null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DocumentChunkResponse> getChunkResponses(UUID documentId) {
        if (documentRepository.findById(documentId).isEmpty()) {
            throw new IllegalArgumentException("Document not found");
        }
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(c -> DocumentChunkResponse.builder()
                        .chunkIndex(c.getChunkIndex())
                        .content(c.getContent())
                        .tokenCount(c.getTokenCount())
                        .build())
                .toList();
    }

    /**
     * Upload nhiều file cho một widget; một file lỗi → ném exception (fail toàn bộ batch).
     */
    public List<DocumentResponse> uploadDocumentsCanonical(MultipartFile[] files, UUID widgetId) throws IOException {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Không có file để upload.");
        }
        List<DocumentResponse> out = new ArrayList<>();
        boolean any = false;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            any = true;
            try {
                Document d = uploadAndProcess(file, widgetId);
                out.add(toDocumentResponse(d));
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Upload thất bại cho file: " + file.getOriginalFilename() + ": " + e.getMessage(),
                        e
                );
            }
        }
        if (!any) {
            throw new IllegalArgumentException("Không có file hợp lệ để upload.");
        }
        return out;
    }

    /**
     * Chỉ retry khi FAILED và chưa có chunk/section/table — tránh trùng vector Qdrant.
     */
    @Transactional
    public Document retryFailedDocument(UUID documentId) throws Exception {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (doc.getStatus() != DocumentStatus.FAILED) {
            throw new IllegalArgumentException("Chỉ có thể retry document đang FAILED.");
        }
        if (!documentChunkRepository.findByDocumentId(documentId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Không thể retry an toàn: đã tồn tại chunk trong DB. Xóa document và upload lại.");
        }
        if (!documentSectionRepository.findByDocumentIdOrderByOrderIndexAsc(documentId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Không thể retry an toàn: đã tồn tại section. Xóa document và upload lại.");
        }
        if (!documentTableRepository.findByDocumentIdOrderByOrderIndexAsc(documentId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Không thể retry an toàn: đã tồn tại table. Xóa document và upload lại.");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File gốc không còn trên disk.");
        }
        BytesMultipartFile mf = BytesMultipartFile.fromPath(
                "file",
                path,
                doc.getFileName(),
                doc.getMimeType()
        );
        doc.setStatus(DocumentStatus.PENDING);
        doc.setChunkCount(null);
        documentRepository.save(doc);
        try {
            return executeProcessing(doc, mf);
        } catch (Exception e) {
            doc.setStatus(DocumentStatus.FAILED);
            documentRepository.save(doc);
            throw e;
        }
    }

    @Transactional
    public void softDeleteDocument(UUID id) {
        Document d = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        d.setDeletedAt(LocalDateTime.now());
        documentRepository.save(d);
    }

    /**
     * Đổi widget/chatbot gán cho document cần đồng bộ lại vector — không hỗ trợ trong prompt này.
     */
    public void assignDocument(UUID documentId, UUID targetChatbotId) {
        if (targetChatbotId == null) {
            throw new IllegalArgumentException("chatbotId là bắt buộc.");
        }
        throw new IllegalArgumentException(
                "Gán document sang chatbot khác cần re-index vector trong Qdrant; hiện chưa hỗ trợ.");
    }

    public DocumentResponse toDocumentResponse(Document d) {
        WidgetConfig w = d.getWidgetConfig();
        return DocumentResponse.builder()
                .id(d.getId())
                .filename(d.getFileName())
                .type(d.getFileType())
                .chatbotId(w != null ? w.getId() : null)
                .chatbotName(w != null ? w.getName() : null)
                .chunkCount(d.getChunkCount() != null ? d.getChunkCount() : 0)
                .sizeBytes(d.getFileSize() != null ? d.getFileSize() : 0L)
                .status(toFeStatus(d.getStatus()))
                .progress(progressFor(d))
                .uploadedAt(toUploadedAt(d))
                .error(d.getStatus() == DocumentStatus.FAILED ? "Xử lý tài liệu thất bại." : null)
                .build();
    }

    /** ISO-like string — đơn giản hóa dùng system default zone */
    private static String toUploadedAt(Document d) {
        if (d.getCreatedAt() == null) {
            return null;
        }
        return d.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
    }

    private static String toFeStatus(DocumentStatus s) {
        if (s == null) {
            return "PROCESSING";
        }
        return switch (s) {
            case COMPLETED -> "INDEXED";
            case PENDING, PROCESSING -> "PROCESSING";
            case FAILED -> "FAILED";
        };
    }

    private static int progressFor(Document d) {
        DocumentStatus s = d.getStatus();
        if (s == null) {
            return 0;
        }
        return switch (s) {
            case COMPLETED -> 100;
            case FAILED -> 0;
            case PROCESSING -> 50;
            case PENDING -> 0;
        };
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
                            .sectionOrder(chunk.sectionOrder())
                            .tokenCount(chunk.tokenCount())
                            .headingLevel(chunk.headingLevel())
                            .childSectionIds(chunk.childSectionIds())
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