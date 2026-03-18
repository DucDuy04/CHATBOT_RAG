package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentRepository;
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
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentParserService documentParserService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public Document uploadAndProcess(MultipartFile file) throws IOException {
        // 1. Lưu file vào thư mục uploads/
        String savedPath = saveFile(file);

        // 2. Tạo bản ghi trong database với trạng thái PENDING
        Document document = Document.builder()
                .fileName(file.getOriginalFilename())
                .filePath(savedPath)
                .fileType(getFileType(file.getOriginalFilename()))
                .fileSize(file.getSize())
                .status(Document.DocumentStatus.PENDING)
                .build();
        document = documentRepository.save(document);

        // 3. Xử lý document (parse → chunk → embed → lưu Qdrant)
        try {
            document.setStatus(Document.DocumentStatus.PROCESSING);
            documentRepository.save(document);

            // Parse
            String text = documentParserService.parse(file);
            log.info("Parse xong: {} ký tự", text.length());

            // Chunk
            List<String> chunks = chunkingService.chunk(text);
            log.info("Chunk xong: {} chunks", chunks.size());

            // Embed + lưu Qdrant
            embeddingService.embedAndStore(chunks, document.getId(), document.getFileName());

            // Cập nhật trạng thái COMPLETED
            document.setStatus(Document.DocumentStatus.COMPLETED);
            document.setChunkCount(chunks.size());
            document.setProcessedAt(LocalDateTime.now());
            documentRepository.save(document);

            log.info("Xử lý xong document: {}", document.getFileName());

        } catch (Exception e) {
            // Nếu có lỗi, đánh dấu FAILED
            document.setStatus(Document.DocumentStatus.FAILED);
            documentRepository.save(document);
            log.error("Lỗi xử lý document {}: {}", document.getFileName(), e.getMessage());
            throw e;
        }

        return document;
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    private String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);

        // Tạo thư mục nếu chưa có
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Tránh trùng tên file bằng cách thêm timestamp
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return filePath.toString();
    }

    private String getFileType(String fileName) {
        if (fileName == null)
            return "UNKNOWN";
        if (fileName.toLowerCase().endsWith(".pdf"))
            return "PDF";
        if (fileName.toLowerCase().endsWith(".txt"))
            return "TXT";
        return "UNKNOWN";
    }
}