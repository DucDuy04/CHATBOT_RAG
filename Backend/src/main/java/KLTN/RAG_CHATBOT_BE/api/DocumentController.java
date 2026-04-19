package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.dto.DocumentListItemResponse;
import KLTN.RAG_CHATBOT_BE.dto.DocumentUploadResponse;
import KLTN.RAG_CHATBOT_BE.service.DocumentService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // POST /api/documents/upload
    @PostMapping("/upload/{widgetId}")
    public ResponseEntity<DocumentUploadResponse> upload(
            @PathVariable UUID widgetId,
            @RequestParam("file") MultipartFile file
            ) {

        try {
            Document document = documentService.uploadAndProcess(file, widgetId);

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

    // GET /api/documents — lấy danh sách tất cả tài liệu
    @GetMapping
    public ResponseEntity<List<DocumentListItemResponse>> getAllDocuments() {
        List<DocumentListItemResponse> response = documentService.getAllDocuments()
                .stream()
                .map(this::toListItemResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    private DocumentListItemResponse toListItemResponse(Document document) {
        return DocumentListItemResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .fileType(document.getFileType())
                .status(document.getStatus() == null ? null : document.getStatus().name())
                .chunkCount(document.getChunkCount())
                .widgetConfigId(document.getWidgetConfig() == null ? null : document.getWidgetConfig().getId())
                .build();
    }
}
