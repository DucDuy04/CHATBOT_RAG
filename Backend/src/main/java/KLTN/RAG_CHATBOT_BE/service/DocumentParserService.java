package KLTN.RAG_CHATBOT_BE.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    // 1. Cập nhật hàm parsePdf: Thêm biến currentHeader để lưu trạng thái qua từng
    // trang
  private String parsePdf(MultipartFile file) throws IOException {
    byte[] bytes = file.getBytes();

    try (PDDocument document = Loader.loadPDF(bytes)) {
        StringBuilder fullContent = new StringBuilder();
        ObjectExtractor extractor = new ObjectExtractor(document);
        SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
        int totalPages = document.getNumberOfPages();
        String[] currentHeader = { "" };

        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {

            // 1. Extract text thường
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setWordSeparator(" ");
            stripper.setLineSeparator("\n");
            stripper.setStartPage(pageNum);
            stripper.setEndPage(pageNum);
            String pageText = stripper.getText(document);
            fullContent.append(cleanText(pageText)).append("\n");

            // 2. Extract table
            try {
                Page page = extractor.extract(pageNum);
                List<Table> tables = sea.extract(page);
                for (Table table : tables) {
                    fullContent.append("\n[TABLE_START]\n");
                    fullContent.append(convertTableToMarkdown(table, currentHeader));
                    fullContent.append("[TABLE_END]\n");
                }
            } catch (Exception e) {
                // bỏ qua nếu không có table
            }
        }
        return fullContent.toString();
    }
}
   
    // 2. Cập nhật hàm convertTableToMarkdown: Nhận diện và chèn Header cũ
    private String convertTableToMarkdown(Table table, String[] currentHeader) {
        StringBuilder sb = new StringBuilder();
        List<List<RectangularTextContainer>> rows = table.getRows();

        if (rows.isEmpty())
            return "";

        List<RectangularTextContainer> firstRow = rows.get(0);

        // Kiểm tra xem dòng đầu tiên là Dữ liệu (Data) hay Tiêu đề (Header)
        if (isDataRow(firstRow)) {
            // LÀ DỮ LIỆU: Có nghĩa đây là phần tiếp nối của bảng từ trang trước!
            // Chèn Header cũ vào trước
            if (!currentHeader[0].isEmpty()) {
                sb.append(currentHeader[0]).append("\n");
            }

            // In dữ liệu ra như bình thường
            for (List<RectangularTextContainer> row : rows) {
                sb.append("| ");
                for (RectangularTextContainer cell : row) {
                    sb.append(cell.getText().trim().replace("\n", " ")).append(" | ");
                }
                sb.append("\n");
            }
        } else {
            // LÀ TIÊU ĐỀ: Đây là một bảng mới. Cần tạo Header và lưu lại.
            StringBuilder headerSb = new StringBuilder("| ");
            StringBuilder separatorSb = new StringBuilder("| ");

            for (RectangularTextContainer cell : firstRow) {
                headerSb.append(cell.getText().trim().replace("\n", " ")).append(" | ");
                separatorSb.append("--- | ");
            }

            // Cập nhật lại Header hiện tại để dành cho trang sau
            currentHeader[0] = headerSb.toString() + "\n" + separatorSb.toString();

            // Ghi Header vào chuỗi kết quả
            sb.append(currentHeader[0]).append("\n");

            // In các dòng dữ liệu còn lại (bỏ qua dòng 0 vì đã là Header)
            for (int i = 1; i < rows.size(); i++) {
                sb.append("| ");
                for (RectangularTextContainer cell : rows.get(i)) {
                    sb.append(cell.getText().trim().replace("\n", " ")).append(" | ");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // Hàm phán đoán xem một dòng có phải là dòng chứa dữ liệu (Data) hay không
    // private boolean isDataRow(List<RectangularTextContainer> row) {
    //     if (row == null || row.isEmpty()) {
    //         return false;
    //     }

    //     int numericCellCount = 0;

    //     for (RectangularTextContainer cell : row) {
    //         String text = cell.getText().trim();
    //         // Kiểm tra xem nội dung ô có chứa chữ số nào không (ví dụ: "100g", "208 cal",
    //         // "13")
    //         if (text.matches(".*\\d+.*")) {
    //             numericCellCount++;
    //         }
    //     }

    //     // Logic (Heuristic):
    //     // Tiêu đề (Header) thường toàn chữ (Ví dụ: "Món Ăn", "Calo", "Protein").
    //     // Nếu dòng có từ 1-2 ô trở lên chứa chữ số, khả năng rất cao nó là Data Row.
    //     // Bạn có thể chỉnh sửa số "1" này tùy theo đặc thù tài liệu của bạn.
    //     return numericCellCount >= 1;
    // }

    private boolean isDataRow(List<RectangularTextContainer> row) {
    if (row == null || row.isEmpty()) return false;

    int numericOnlyCellCount = 0;

    for (RectangularTextContainer cell : row) {
        String text = cell.getText().trim();
        // ✅ Chỉ tính là data nếu ô TOÀN SỐ hoặc số + đơn vị (100g, 7h, 12h)
        // Header thường là text dài hơn như "Thời Điểm", "Đồ Ăn Đề Nghị"
        if (text.matches("^[\\d\\s:h.,]+$") || text.matches("^\\d+[a-zA-Z]*$")) {
            numericOnlyCellCount++;
        }
    }

    // Chỉ là data row nếu PHẦN LỚN ô là số thuần
    return numericOnlyCellCount >= (row.size() / 2.0);
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