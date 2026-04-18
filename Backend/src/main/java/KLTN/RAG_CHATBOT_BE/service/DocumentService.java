package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig; // Import Entity Widget (Ngày 1)
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository; // Import Repository Widget (Ngày 1)
import KLTN.RAG_CHATBOT_BE.domain.enums.DocumentStatus; // Đảm bảo dùng đúng Enum trạng thái

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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final WidgetConfigRepository widgetConfigRepository; // Inject thêm Repository này
    private final DocumentParserService documentParserService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    // NHẬN THÊM widgetId TỪ CONTROLLER
    public Document uploadAndProcess(MultipartFile file, UUID widgetId) throws IOException {
        
        // 1. Kiểm tra xem Widget có tồn tại không
        WidgetConfig widgetConfig = widgetConfigRepository.findById(widgetId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Widget với ID: " + widgetId));

        // 2. Lưu file vào thư mục uploads/
        String savedPath = saveFile(file);

        // 3. Tạo bản ghi trong database với trạng thái PENDING và GẮN WIDGET
        Document document = Document.builder()
                .widgetConfig(widgetConfig) // QUAN TRỌNG: Gắn tài liệu này cho Widget nào
                .fileName(file.getOriginalFilename())
                .filePath(savedPath)
                .fileType(getFileType(file.getOriginalFilename()))
                .fileSize(file.getSize())
                .status(DocumentStatus.PENDING) // Sửa lại cách gọi Enum cho chuẩn Java
                .build();
        document = documentRepository.save(document);

        // 4. Xử lý document (parse → chunk → embed → lưu Qdrant)
        try {
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            // Parse
            String text = documentParserService.parse(file);
            log.info("Parse xong: {} ký tự", text.length());

            // Chunk
            List<String> chunks = chunkingService.chunk(text);
            log.info("Chunk xong: {} chunks", chunks.size());

            // Embed + lưu Qdrant (ĐÃ SỬA LỖI SYNTAX VÀ THÊM WIDGET_ID)
            // (Lưu ý: Nếu document.getId() của bạn là UUID, mà hàm bên EmbeddingService đang nhận Long thì bạn cần đổi bên EmbeddingService thành UUID nhé)
            embeddingService.embedAndStore(chunks, document.getId(), document.getFileName(), widgetId);

            // Cập nhật trạng thái COMPLETED
            document.setStatus(DocumentStatus.COMPLETED);
            document.setChunkCount(chunks.size());
            // document.setProcessedAt(LocalDateTime.now()); // Entity Ngày 1 dùng updatedAt tự động cập nhật, bạn có thể bỏ dòng này.
            documentRepository.save(document);

            log.info("Xử lý xong document: {}", document.getFileName());

        } catch (Exception e) {
            // Nếu có lỗi, đánh dấu FAILED
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            log.error("Lỗi xử lý document {}: {}", document.getFileName(), e.getMessage());
            throw e; // Ném lỗi ra để Controller biết
        }

        return document;
    }

    // MULTI-TENANT: Không nên lấy "Tất cả", mà chỉ lấy tài liệu của Widget đó thôi
    public List<Document> getDocumentsByWidget(UUID widgetId) {
        // Đảm bảo bạn đã thêm hàm findByWidgetConfigId(UUID id) vào DocumentRepository ở Ngày 1
        return documentRepository.findByWidgetConfigId(widgetId);
    }

    private String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return filePath.toString();
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "UNKNOWN";
        if (fileName.toLowerCase().endsWith(".pdf")) return "PDF";
        if (fileName.toLowerCase().endsWith(".txt")) return "TXT";
        return "UNKNOWN";
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }
}