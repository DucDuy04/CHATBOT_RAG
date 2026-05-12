package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.DocumentAssignRequest;
import KLTN.RAG_CHATBOT_BE.dto.DocumentChunkResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentPageResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentStatusResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentUploadResponse;
import KLTN.RAG_CHATBOT_BE.dto.SimpleSuccessResponse;
import KLTN.RAG_CHATBOT_BE.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String MSG_TENANT_REQUIRED = "chatbotId is required for document upload";

    private final DocumentService documentService;
    private final WidgetConfigRepository widgetConfigRepository;

    /** Legacy: POST /api/documents/upload/{widgetId} — giữ nguyên behavior cũ cho ingest có widget trong path. */
    @PostMapping(value = "/upload/{widgetId}")
    public ResponseEntity<DocumentUploadResponse> uploadLegacy(
            @PathVariable UUID widgetId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            var document = documentService.uploadAndProcess(file, widgetId);

            return ResponseEntity.ok(DocumentUploadResponse.builder()
                    .id(document.getId())
                    .fileName(document.getFileName())
                    .status(document.getStatus().name())
                    .message("Upload và xử lý thành công! Đã tạo "
                            + document.getChunkCount() + " chunks.")
                    .build());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    DocumentUploadResponse.builder()
                            .status("FAILED")
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    DocumentUploadResponse.builder()
                            .status("FAILED")
                            .message("Lỗi hệ thống: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Canonical FE: POST /api/documents/upload — multipart {@code files}, tenant qua {@code chatbotId} hoặc {@code widgetId}
     * (form field hoặc query).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCanonical(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "chatbotId", required = false) String chatbotId,
            @RequestParam(value = "widgetId", required = false) String widgetId
    ) {
        String raw = null;
        if (chatbotId != null && !chatbotId.isBlank()) {
            raw = chatbotId.trim();
        } else if (widgetId != null && !widgetId.isBlank()) {
            raw = widgetId.trim();
        }
        if (raw == null) {
            return ResponseEntity.badRequest().body(Map.of("message", MSG_TENANT_REQUIRED));
        }
        final UUID tenantId;
        try {
            tenantId = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid chatbotId or widgetId"));
        }
        if (!widgetConfigRepository.existsById(tenantId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chatbot/widget không tồn tại."));
        }
        try {
            List<DocumentResponse> uploaded = documentService.uploadDocumentsCanonical(files, tenantId);
            return ResponseEntity.ok(uploaded);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listDocuments(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "") String type,
            @RequestParam(required = false, defaultValue = "") String chatbotId,
            @RequestParam(required = false, defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            DocumentPageResponse body = documentService.listDocuments(search, type, chatbotId, status, page, size);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Bad request";
            return ResponseEntity.badRequest().body(Map.of("message", msg));
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid document id"));
        }
        try {
            DocumentStatusResponse dto = documentService.getDocumentStatusDto(id);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Document not found"));
        }
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<?> getChunks(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid document id"));
        }
        try {
            List<DocumentChunkResponse> chunks = documentService.getChunkResponses(id);
            return ResponseEntity.ok(chunks);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Document not found"));
        }
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<?> assign(
            @PathVariable("id") String documentIdRaw,
            @RequestBody(required = false) DocumentAssignRequest request
    ) {
        UUID documentId;
        try {
            documentId = UUID.fromString(documentIdRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid document id"));
        }
        try {
            documentService.assignDocument(documentId, request != null ? request.getChatbotId() : null);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "Assignment not supported"));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid document id"));
        }
        try {
            DocumentResponse dto = documentService.toDocumentResponse(documentService.retryFailedDocument(id));
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Document not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Document not found"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", msg != null ? msg : "Retry failed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message",
                    e.getMessage() != null ? e.getMessage() : "Retry failed"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid document id"));
        }
        try {
            documentService.softDeleteDocument(id);
            return ResponseEntity.ok(SimpleSuccessResponse.builder().success(true).build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Document not found"));
        }
    }
}

