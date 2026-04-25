package KLTN.RAG_CHATBOT_BE.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import KLTN.RAG_CHATBOT_BE.record.Section;

@Service
public class DocumentParserService {

    private static final Pattern SECTION_HEADER_PATTERN =
        Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?[ \\t]+([^\\n]{3,120})$", Pattern.MULTILINE);

    private static final Pattern SECTION_NUMBER_PATTERN =
        Pattern.compile("^(\\d+(?:\\.\\d+)*)[.\\s]");
    
    // ==========================================
    // 1. ENTRY POINT (HÀM GỌI CHÍNH)
    // ==========================================
    public List<Section> parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }

        // Dù là định dạng gì thì Output cuối cùng luôn là List<Section>
        if (fileName.toLowerCase().endsWith(".pdf")) {
            return parsePdf(file);
        } else if (fileName.toLowerCase().endsWith(".txt")) {
            return parseTxt(file);
        } else {
            throw new IllegalArgumentException(
                    "Chỉ hỗ trợ file PDF và TXT. File bạn upload: " + fileName);
        }
    }

    // ==========================================
    // 2. PARSE PDF
    // ==========================================
    private List<Section> parsePdf(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();

        try (PDDocument document = Loader.loadPDF(bytes)) {
            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
            int totalPages = document.getNumberOfPages();
            String[] currentHeader = { "" };
            
            Map<Integer, String> pageContents = new java.util.HashMap<>();

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                StringBuilder pageBuilder = new StringBuilder();

                // 1. Extract text thường
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setWordSeparator(" ");
                stripper.setLineSeparator("\n");
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                
                String pageText = stripper.getText(document);
                pageBuilder.append(cleanText(pageText)).append("\n");

                // 2. Extract table (Gắn thẳng table vào nội dung của trang hiện tại)
                try {
                    Page page = extractor.extract(pageNum);
                    List<Table> tables = sea.extract(page);
                    for (Table table : tables) {
                        String tableMarkdown = convertTableToMarkdown(table, currentHeader);
                        if (!isUsableTable(table, tableMarkdown)) {
                            continue;
                        }

                        pageBuilder.append("\n[TABLE_START]\n");
                        pageBuilder.append(tableMarkdown);
                        pageBuilder.append("[TABLE_END]\n");
                    }
                } catch (Exception e) {
                    // bỏ qua nếu không có table
                }

                // Lưu nội dung hoàn chỉnh của trang (gồm cả Text + Table) vào Map
                pageContents.put(pageNum, pageBuilder.toString());
            }
            
            // Xử lý chia Section
            return parseSections(pageContents); 
        }
    }

    // ==========================================
    // 3. PARSE TXT
    // ==========================================
    private List<Section> parseTxt(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        String cleanedText = cleanText(text);

        // Quy ước toàn bộ file TXT là Trang 1
        Map<Integer, String> pageContents = Map.of(1, cleanedText);

        // Đưa cho hàm Regex phân tích như bình thường
        return parseSections(pageContents);
    }

    // ==========================================
    // 4. CORE LOGIC: CHIA SECTION TỪ MAP THEO TRANG
    // ==========================================
    public List<Section> parseSections(Map<Integer, String> pageContents) {
        List<Section> sections = new ArrayList<>();
        TreeMap<Integer, String> sortedPages = new TreeMap<>(pageContents);
        
        // Mặc định tên Header là General đề phòng file (như TXT) không có Header nào
        String currentHeader = "General"; 
        StringBuilder currentContent = new StringBuilder();
        int startPage = 1;

        for (Map.Entry<Integer, String> entry : sortedPages.entrySet()) {
            int currentPage = entry.getKey();
            String pageText = entry.getValue();
            
            Matcher matcher = SECTION_HEADER_PATTERN.matcher(pageText);
            int lastEndIndex = 0;

            while (matcher.find()) {
                // Lưu content cũ vào trước khi chuyển sang header mới
                if (currentContent.length() > 0 || lastEndIndex > 0) {
                    currentContent.append(pageText, lastEndIndex, matcher.start());
                    
                    String finalizedContent = currentContent.toString().trim();
                    if (!finalizedContent.isEmpty()) {
                        sections.add(new Section(currentHeader, startPage, currentPage, finalizedContent));
                    }
                    currentContent.setLength(0); 
                }

                currentHeader = matcher.group(0).trim();
                startPage = currentPage;
                lastEndIndex = matcher.end();
            }

            // Text còn lại của trang
            currentContent.append(pageText.substring(lastEndIndex)).append("\n");
        }

        // Lưu chunk cuối cùng
        String finalChunkContent = currentContent.toString().trim();
        if (!finalChunkContent.isEmpty()) {
            sections.add(new Section(currentHeader, startPage, sortedPages.lastKey(), finalChunkContent));
        }

        // Gộp các Section quá nhỏ (< 1200 chars) để tối ưu VectorDB
        return mergeSmallSections(sections, 1200); 
    }

    // ==========================================
    // 5. HELPER METHODS (MERGE, TABLE, CLEAN TEXT)
    // ==========================================
    public List<Section> mergeSmallSections(List<Section> originalSections, int minCharLength) {
        if (originalSections == null || originalSections.isEmpty()) return new ArrayList<>();

        List<Section> mergedSections = new ArrayList<>();
        Section currentMerge = originalSections.get(0);

        for (int i = 1; i < originalSections.size(); i++) {
            Section nextSec = originalSections.get(i);

            boolean tooSmall = currentMerge.content().length() < minCharLength;
            // Chỉ gộp khi cùng section cha (ví dụ 10.1 + 10.2 → ok, 10.x + 11.x → không)
            boolean sameParent = isSameParentSection(currentMerge.header(), nextSec.header());

            if (tooSmall && sameParent) {
                // Giữ header của section chính (section đầu), embed nội dung section tiếp theo
                String mergedContent = currentMerge.content()
                        + "\n\n[" + nextSec.header() + "]\n" + nextSec.content();
                currentMerge = new Section(currentMerge.header(), currentMerge.startPage(),
                        nextSec.endPage(), mergedContent);
            } else {
                mergedSections.add(currentMerge);
                currentMerge = nextSec;
            }
        }
        mergedSections.add(currentMerge);
        return mergedSections;
    }

    private String convertTableToMarkdown(Table table, String[] currentHeader) {
        StringBuilder sb = new StringBuilder();
        List<List<RectangularTextContainer>> rows = table.getRows();

        if (rows.isEmpty()) return "";

        List<RectangularTextContainer> firstRow = rows.get(0);

        if (isDataRow(firstRow)) {
            if (!currentHeader[0].isEmpty()) sb.append(currentHeader[0]).append("\n");
            for (List<RectangularTextContainer> row : rows) {
                sb.append("| ");
                for (RectangularTextContainer cell : row) {
                    sb.append(cell.getText().trim().replace("\n", " ")).append(" | ");
                }
                sb.append("\n");
            }
        } else {
            StringBuilder headerSb = new StringBuilder("| ");
            StringBuilder separatorSb = new StringBuilder("| ");

            for (RectangularTextContainer cell : firstRow) {
                headerSb.append(cell.getText().trim().replace("\n", " ")).append(" | ");
                separatorSb.append("--- | ");
            }

            currentHeader[0] = headerSb.toString() + "\n" + separatorSb.toString();
            sb.append(currentHeader[0]).append("\n");

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

    private boolean isUsableTable(Table table, String markdown) {
        if (table == null || markdown == null || markdown.isBlank()) {
            return false;
        }

        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows == null || rows.size() < 3) {
            return false;
        }

        int maxColumns = rows.stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);
        if (maxColumns < 3) {
            return false;
        }

        int nonEmptyCells = 0;
        int textChars = 0;
        for (List<RectangularTextContainer> row : rows) {
            for (RectangularTextContainer cell : row) {
                String text = cell.getText() == null ? "" : cell.getText().trim();
                if (!text.isBlank()) {
                    nonEmptyCells++;
                    textChars += text.length();
                }
            }
        }

        return nonEmptyCells >= 6 && textChars >= 80;
    }

    private boolean isDataRow(List<RectangularTextContainer> row) {
        if (row == null || row.isEmpty()) return false;
        int numericOnlyCellCount = 0;
        for (RectangularTextContainer cell : row) {
            String text = cell.getText().trim();
            if (text.matches("^[\\d\\s:h.,]+$") || text.matches("^\\d+[a-zA-Z]*$")) {
                numericOnlyCellCount++;
            }
        }
        return numericOnlyCellCount >= (row.size() / 2.0);
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text
                .replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ')
                .replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "")
                .replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                // Chỉ gộp newline thành space khi ký tự tiếp theo KHÔNG phải:
                // - \n (dòng trắng)  - \d (đầu heading số như "10.1")  - [ (marker TABLE_START)
                .replaceAll("(?<!\\n)\\n(?![\\n\\d\\[])", " ")
                .replaceAll("\\.{2,}", ".")
                .trim();
    }

    // ==========================================
    // 6. SECTION UTILITY
    // ==========================================
    /**
     * Trích xuất số section từ header (ví dụ "10.1 Tên mục" → "10.1").
     * Trả về null nếu header không bắt đầu bằng pattern số.
     */
    public String extractSectionNumber(String header) {
        if (header == null || header.isBlank()) return null;
        java.util.regex.Matcher m = SECTION_NUMBER_PATTERN.matcher(header.trim());
        return m.find() ? m.group(1) : null;
    }

    /**
     * Trích xuất số section cha (ví dụ "10.1" → "10", "3.2.1" → "3.2").
     * Với section cấp 1 (ví dụ "10") trả về chính nó.
     */
    public String extractParentSectionNumber(String sectionNumber) {
        if (sectionNumber == null || sectionNumber.isBlank()) return "";
        int lastDot = sectionNumber.lastIndexOf('.');
        return lastDot > 0 ? sectionNumber.substring(0, lastDot) : sectionNumber;
    }

    /**
     * Kiểm tra hai header có cùng section cha không (ví dụ "10.1" và "10.2" đều thuộc "10").
     */
    public boolean isSameParentSection(String header1, String header2) {
        String n1 = extractSectionNumber(header1);
        String n2 = extractSectionNumber(header2);
        if (n1 == null || n2 == null) return false;
        String p1 = extractParentSectionNumber(n1);
        String p2 = extractParentSectionNumber(n2);
        return !p1.isBlank() && p1.equals(p2);
    }

}

// record Section(String header, int startPage, int endPage, String content) {}
