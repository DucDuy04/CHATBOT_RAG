package KLTN.RAG_CHATBOT_BE.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class DocumentParserService {

    // Đọc nội dung file, trả về chuỗi text thuần
    public String parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }

        if (fileName.toLowerCase().endsWith(".pdf")) {
            return parsePdf(file);
        } else if (fileName.toLowerCase().endsWith(".txt")) {
            return parseTxt(file);
        } else {
            throw new IllegalArgumentException(
                    "Chỉ hỗ trợ file PDF và TXT. File bạn upload: " + fileName);
        }
    }

    // private String parsePdf(MultipartFile file) throws IOException {
    // // PDFBox đọc từng trang và gộp lại thành 1 chuỗi
    // try (PDDocument document = Loader.loadPDF(file.getBytes())) {
    // PDFTextStripper stripper = new PDFTextStripper();
    // String text = stripper.getText(document);

    // // Dọn dẹp ký tự thừa
    // return cleanText(text);
    // }
    // }
    private String parsePdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setWordSeparator(" ");
            stripper.setLineSeparator("\n");
            String text = stripper.getText(document);
            return cleanText(text);
        }
    }

    private String parseTxt(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        return cleanText(text);
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace('\u00A0', ' ') // Non-breaking space
                .replace('\u2007', ' ') // Figure space
                .replace('\u202F', ' ') // Narrow no-break space
                .replace("\u200B", "") // Zero-width space
                .replace("\u200C", "") // Zero-width non-joiner
                .replace("\u200D", "") // Zero-width joiner
                .replace("\uFEFF", "") // BOM / zero-width no-break space
                .replaceAll("\\r\\n", "\n") // Chuẩn hóa xuống dòng
                .replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ") // Nhiều khoảng trắng → 1
                .replaceAll("([,.;:!?])(\\S)", "$1 $2") // Đảm bảo có khoảng trắng sau dấu câu
                .replaceAll("\\n{3,}", "\n\n") // Nhiều dòng trống → tối đa 2
                .trim();
    }
}