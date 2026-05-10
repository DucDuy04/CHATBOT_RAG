package KLTN.RAG_CHATBOT_BE.support;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MultipartFile từ byte[] — dùng retry FAILED từ file đã lưu disk (không phụ thuộc spring-test).
 */
public class BytesMultipartFile implements MultipartFile {

    private final String paramName;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public BytesMultipartFile(String paramName, String originalFilename, String contentType, byte[] content) {
        this.paramName = paramName;
        this.originalFilename = originalFilename;
        this.contentType = contentType != null ? contentType : "application/octet-stream";
        this.content = content != null ? content : new byte[0];
    }

    public static BytesMultipartFile fromPath(String paramName, Path path, String originalFilename, String contentType)
            throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new BytesMultipartFile(paramName, originalFilename, contentType, bytes);
    }

    @Override
    public String getName() {
        return paramName;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), content);
    }

    @Override
    public void transferTo(Path dest) throws IOException {
        Files.write(dest, content);
    }
}
