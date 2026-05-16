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
import jakarta.persistence.EntityNotFoundException;
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

    private final QdrantPurgeService qdrantPurgeService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    /** Must match {@link DocumentParserService} supported types. */
    public static final String UNSUPPORTED_UPLOAD_FILE_MSG =
            "Định dạng file chưa được hỗ trợ. Hiện chỉ hỗ trợ PDF và TXT.";

    public Document uploadAndProcess(MultipartFile file, UUID widgetId) throws IOException {
        WidgetConfig widgetConfig = widgetConfigRepository.findById(widgetId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Widget với ID: " + widgetId));

        validateUploadableFile(file);

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
        String statusNorm = normalizeListStatusParam(statusFe);
        UUID chatbotFilter = null;
        if (chatbotIdStr != null && !chatbotIdStr.isBlank()) {
            try {
                chatbotFilter = UUID.fromString(chatbotIdStr.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid chatbotId");
            }
        }
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Specification<Document> spec = buildDocumentSpec(search, type, chatbotFilter, statusNorm);
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

    /**
     * FE may send placeholder labels; treat as no status filter (no 500, no bogus enum parse).
     */
    private static String normalizeListStatusParam(String statusFe) {
        if (statusFe == null || statusFe.isBlank()) {
            return "";
        }
        String t = statusFe.trim();
        if ("ALL STATUSES".equalsIgnoreCase(t) || "ALL STATUS".equalsIgnoreCase(t)) {
            return "";
        }
        return t;
    }

    private Specification<Document> buildDocumentSpec(
            String search,
            String type,
            UUID chatbotFilter,
            String statusFe
    ) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                preds.add(cb.like(cb.lower(root.get("fileName")), "%" + search.trim().toLowerCase() + "%"));
            }
            if (type != null && !type.isBlank()) {
                preds.add(cb.equal(cb.upper(root.get("fileType")), type.trim().toUpperCase()));
            }
            if (chatbotFilter != null) {
                Join<Object, Object> widgetJoin = root.join("widgetConfig", JoinType.INNER);
                preds.add(cb.equal(widgetJoin.get("id"), chatbotFilter));
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
            // Hibernate rejects cb.and() with zero predicates (e.g. no search/type/chatbot/status).
            if (preds.isEmpty()) {
                return cb.conjunction();
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
     * Retry chỉ khi {@link DocumentStatus#FAILED}. Nếu ingestion lỗi giữa chừng đã tạo
     * section/table/chunk hoặc vector Qdrant một phần: purge Qdrant (cùng filter như delete),
     * rồi hard-delete toàn bộ children của document đó để tránh trộn dữ liệu và tránh
     * vi phạm unique (document_id, chunk_index) / section_key / table_key khi insert lại.
     * Thứ tự: kiểm tra file gốc còn tồn tại → purge Qdrant (fail thì dừng) → xóa DB → chạy lại pipeline.
     */
    @Transactional
    public Document retryFailedDocument(UUID documentId) throws Exception {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (doc.getStatus() != DocumentStatus.FAILED) {
            throw new IllegalArgumentException("Chỉ có thể retry document đang FAILED.");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File gốc không còn trên disk.");
        }
        UUID widgetId = resolveWidgetIdForPurge(doc);
        qdrantPurgeService.purgeDocumentVectors(documentId, widgetId);

        documentChunkRepository.unlinkNeighborsByDocumentId(documentId);
        documentChunkRepository.hardDeleteByDocumentId(documentId);
        documentTableRepository.hardDeleteByDocumentId(documentId);
        documentSectionRepository.hardDeleteByDocumentId(documentId);

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

    /**
     * Purge Qdrant vectors for this document+tenant, then soft-delete the row.
     * If Qdrant purge fails, the document is not deleted and an {@link IllegalStateException} is thrown.
     */
    @Transactional
    public void softDeleteDocument(UUID id) {
        Document d = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        UUID widgetId = resolveWidgetIdForPurge(d);
        qdrantPurgeService.purgeDocumentVectors(d.getId(), widgetId);
        LocalDateTime ts = LocalDateTime.now();
        // Child rows keep their own deleted_at; @SQLRestriction on chunks/sections/tables only filters
        // those columns — without this, RAG retrieval still loads rows whose parent document is deleted.
        documentTableRepository.softDeleteByDocumentId(id, ts);
        documentSectionRepository.softDeleteByDocumentId(id, ts);
        documentChunkRepository.softDeleteByDocumentId(id, ts);
        d.setDeletedAt(ts);
        documentRepository.save(d);
    }

    private UUID resolveWidgetIdForPurge(Document d) {
        try {
            if (d.getWidgetConfig() != null) {
                return d.getWidgetConfig().getId();
            }
        } catch (EntityNotFoundException ignored) {
            // Widget soft-deleted — read FK from documents table
        }
        return documentRepository.findWidgetConfigIdUuidStringForPurge(d.getId())
                .map(String::trim)
                .map(UUID::fromString)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    /**
     * Reject unsupported uploads before writing to disk or creating a {@link Document} row.
     * Allowed: {@code .pdf}, {@code .txt} (case-insensitive). MIME: pdf, plain text, or generic binary when extension is allowed.
     */
    static void validateUploadableFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Không có file để upload.");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException(UNSUPPORTED_UPLOAD_FILE_MSG);
        }
        String lower = original.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".txt")) {
            throw new IllegalArgumentException(UNSUPPORTED_UPLOAD_FILE_MSG);
        }
        String mime = file.getContentType();
        if (mime == null || mime.isBlank()) {
            return;
        }
        String m = mime.toLowerCase().trim();
        boolean ok = m.equals("application/pdf")
                || m.startsWith("text/plain")
                || m.equals("application/octet-stream")
                || m.equals("binary/octet-stream");
        if (!ok) {
            throw new IllegalArgumentException(UNSUPPORTED_UPLOAD_FILE_MSG);
        }
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
        UUID chatbotId = null;
        String chatbotName = "Unknown chatbot";
        WidgetConfig w = d.getWidgetConfig();
        if (w != null) {
            try {
                chatbotId = w.getId();
                String n = w.getName();
                chatbotName = (n != null && !n.isBlank()) ? n : "Unknown chatbot";
            } catch (EntityNotFoundException ex) {
                // Widget row missing or soft-deleted (@SQLRestriction) while document still references FK
                chatbotId = null;
                chatbotName = "Unknown chatbot";
            }
        }
        String filename = d.getFileName() != null ? d.getFileName() : "";
        String fileType = d.getFileType() != null ? d.getFileType() : "UNKNOWN";
        return DocumentResponse.builder()
                .id(d.getId())
                .filename(filename)
                .type(fileType)
                .chatbotId(chatbotId)
                .chatbotName(chatbotName)
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