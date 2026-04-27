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
import lombok.extern.slf4j.Slf4j;

import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import KLTN.RAG_CHATBOT_BE.record.Section;

@Slf4j
@Service
public class DocumentParserService {

    private static final Pattern SECTION_HEADER_PATTERN =
        // NOTE:
        // - Chỉ match dạng "2.1 Tiêu đề" ở đầu dòng.
        // - Việc lọc false-positive (table rows, bullet/list, footer/header...) được xử lý thêm ở isLikelySectionHeader().
        // PDF thường thụt indent cho subheading (vd "    6.4 API Structure ...") nên phải cho phép leading spaces.
        Pattern.compile("(?m)^\\s{0,16}(\\d+(?:\\.\\d+)*)\\.?[ \\t]+([^\\n]{3,160})$");

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
            String lastTableHeader = null;
            int lastTableHeaderPage = -1;

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
                        // Nếu table ở trang hiện tại là continuation (không có header row),
                        // và table ngay trước đó có cùng header → ghép rows vào table trước để tránh bị split context.
                        boolean isContinuation = isContinuationTable(table, currentHeader[0]);
                        boolean canMergeToPrev =
                                isContinuation
                                        && pageNum > 1
                                        && lastTableHeader != null
                                        && lastTableHeader.equals(currentHeader[0])
                                        && lastTableHeaderPage == (pageNum - 1)
                                        && pageContents.containsKey(pageNum - 1);

                        if (canMergeToPrev) {
                            String prev = pageContents.get(pageNum - 1);
                            String rowsOnly = removeMarkdownHeader(tableMarkdown, currentHeader[0]);
                            pageContents.put(pageNum - 1, appendRowsIntoLastTable(prev, rowsOnly));
                        } else {
                            pageBuilder.append("\n[TABLE_START]\n");
                            pageBuilder.append(tableMarkdown);
                            pageBuilder.append("[TABLE_END]\n");
                            lastTableHeader = currentHeader[0];
                            lastTableHeaderPage = pageNum;
                        }
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
        java.util.Map<String, Integer> seenSectionNumberCounts = new java.util.HashMap<>();
        String currentSectionNumber = null;

        for (Map.Entry<Integer, String> entry : sortedPages.entrySet()) {
            int currentPage = entry.getKey();
            String pageText = entry.getValue();
            
            // IMPORTANT:
            // - Không được nhận nhầm heading nằm trong TABLE (markdown rows) thành section heading.
            // - Vì pageText đã có marker [TABLE_START]/[TABLE_END], ta mask nội dung table để regex không match bên trong.
            String maskedForHeadingScan = maskTableBlocks(pageText);
            Matcher matcher = SECTION_HEADER_PATTERN.matcher(maskedForHeadingScan);
            int lastEndIndex = 0;

            while (matcher.find()) {
                String candidateHeader = matcher.group(0).trim();
                if (!isLikelySectionHeader(candidateHeader)) {
                    continue;
                }
                log.debug("[Parse] Heading candidate page={}, idx={}, header='{}'",
                        currentPage, matcher.start(), candidateHeader);

                // Nếu header lặp đúng header hiện tại → coi như page header/footer, không mở section mới.
                if (candidateHeader.equals(currentHeader)) {
                    continue;
                }

                // Nếu section number đã từng xuất hiện trước đó, mà lại xuất hiện ở đầu trang (header/footer) → bỏ qua.
                String candidateNumber = extractSectionNumber(candidateHeader);
                if (candidateNumber != null) {
                    int count = seenSectionNumberCounts.getOrDefault(candidateNumber, 0);
                    boolean appearsNearTopOfPage = matcher.start() <= 320; // heuristic: vùng header thường nằm rất gần đầu trang
                    if (count > 0 && appearsNearTopOfPage) {
                        log.debug("[Parse] Skip repeated section number near page top: page={}, number={}, header='{}'",
                                currentPage, candidateNumber, candidateHeader);
                        continue;
                    }

                    // Reject numbered-list/table-step headings (vd "1. Login", "2. Request") xuất hiện bên trong nội dung:
                    // Nếu đang ở trong 1 section top-level lớn hơn, không cho phép "tụt" về số nhỏ hơn (đặc biệt 1..9).
                    if (!isStructurallyConsistentHeading(currentSectionNumber, candidateNumber)) {
                        log.debug("[Parse] Skip structurally-inconsistent heading: page={}, current={}, candidate={}, header='{}'",
                                currentPage, currentSectionNumber, candidateNumber, candidateHeader);
                        continue;
                    }
                }
                // Lưu content cũ vào trước khi chuyển sang header mới
                if (currentContent.length() > 0 || lastEndIndex > 0) {
                    currentContent.append(pageText, lastEndIndex, matcher.start());
                    
                    String finalizedContent = currentContent.toString().trim();
                    // Với heading "cha" thường có thể không có content giữa 2 dòng heading liên tiếp (vd "2." ngay trước "2.1").
                    // Ta vẫn lưu Section nếu currentHeader là heading thật, để giữ đúng hierarchy cho chunk metadata.
                    if (!finalizedContent.isEmpty() || extractSectionNumber(currentHeader) != null) {
                        sections.add(new Section(currentHeader, startPage, currentPage, finalizedContent));
                    }
                    currentContent.setLength(0); 
                }

                currentHeader = matcher.group(0).trim();
                startPage = currentPage;
                lastEndIndex = matcher.end();

                String newNumber = extractSectionNumber(currentHeader);
                if (newNumber != null) {
                    seenSectionNumberCounts.put(newNumber, seenSectionNumberCounts.getOrDefault(newNumber, 0) + 1);
                    currentSectionNumber = newNumber;
                }
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

    /**
     * Quy tắc nhất quán số heading để tránh nhầm numbered list / table steps thành section.
     *
     * Heuristic:
     * - Nếu đang ở trong 1 section top-level N (vd 6.*), thì KHÔNG chấp nhận heading top-level nhỏ hơn N
     *   xuất hiện ở giữa tài liệu (vd 1., 2., 3. trong bảng "steps").
     * - Vẫn cho phép:
     *   - Subheading của section hiện tại (vd 6.4 sau 6)
     *   - Heading top-level tăng (vd 7 sau 6, 10 sau 7)
     *   - Trong giai đoạn đầu doc (chưa có current) thì cho qua.
     */
    private boolean isStructurallyConsistentHeading(String currentNumber, String candidateNumber) {
        if (candidateNumber == null || candidateNumber.isBlank()) return false;
        if (currentNumber == null || currentNumber.isBlank()) return true;

        int currentTop = parseTopLevel(currentNumber);
        int candidateTop = parseTopLevel(candidateNumber);
        if (currentTop <= 0 || candidateTop <= 0) return true;

        boolean candidateIsSubOfCurrent = candidateNumber.startsWith(currentNumber + ".");
        if (candidateIsSubOfCurrent) return true;

        // candidate top-level nhỏ hơn current top-level → reject (điển hình: steps 1..6 trong section 6.*)
        if (!candidateNumber.contains(".") && candidateTop < currentTop) {
            return false;
        }

        return true;
    }

    private int parseTopLevel(String number) {
        try {
            int dot = number.indexOf('.');
            String top = dot > 0 ? number.substring(0, dot) : number;
            return Integer.parseInt(top);
        } catch (Exception ignored) {
            return -1;
        }
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
            // KHÔNG gộp khi current là parent của next (vd "2" + "2.1"), vì sẽ làm mất sectionId/headingPath chính xác.
            boolean isParentChild = isParentOf(currentMerge.header(), nextSec.header());

            if (tooSmall && sameParent && !isParentChild) {
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

    private boolean isParentOf(String headerParent, String headerChild) {
        String p = extractSectionNumber(headerParent);
        String c = extractSectionNumber(headerChild);
        if (p == null || c == null) return false;
        return c.startsWith(p + ".");
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

    private boolean isContinuationTable(Table table, String headerMarkdown) {
        if (table == null) return false;
        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows == null || rows.isEmpty()) return false;
        // convertTableToMarkdown() dùng isDataRow(firstRow) để quyết định có header hay không.
        // Nếu firstRow là data row và đã có header từ trang trước → khả năng cao là continuation.
        return isDataRow(rows.get(0)) && headerMarkdown != null && !headerMarkdown.isBlank();
    }

    private String removeMarkdownHeader(String tableMarkdown, String headerMarkdown) {
        if (tableMarkdown == null) return "";
        if (headerMarkdown == null || headerMarkdown.isBlank()) return tableMarkdown;
        String trimmed = tableMarkdown;
        if (trimmed.startsWith(headerMarkdown)) {
            trimmed = trimmed.substring(headerMarkdown.length());
        }
        // remove leading newlines/spaces after header removal
        return trimmed.replaceFirst("^\\s*\\n*", "");
    }

    private String appendRowsIntoLastTable(String previousPageContent, String rowsMarkdown) {
        if (previousPageContent == null) return "";
        if (rowsMarkdown == null || rowsMarkdown.isBlank()) return previousPageContent;

        int endIdx = previousPageContent.lastIndexOf("[TABLE_END]");
        if (endIdx < 0) {
            // Không tìm thấy TABLE_END thì fallback: nối ở cuối trang trước
            return previousPageContent + "\n" + rowsMarkdown;
        }

        String beforeEnd = previousPageContent.substring(0, endIdx);
        String afterEnd = previousPageContent.substring(endIdx);
        return beforeEnd + "\n" + rowsMarkdown + "\n" + afterEnd;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text
                .replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ')
                .replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "")
                .replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                // Giữ nguyên xuống dòng SAU heading để tránh dính heading với paragraph (sẽ làm heading quá dài và bị reject).
                // Tăng 1 newline sau dòng heading (khi heading line kết thúc bằng '\n' và sau đó là chữ thường).
                .replaceAll(
                        "(?m)^(\\s{0,16}\\d+(?:\\.\\d+)*\\.?\\s+[^\\n]{3,160})\\n(?!\\n)",
                        "$1\n\n"
                )
                // Chỉ gộp newline thành space khi ký tự tiếp theo KHÔNG phải:
                // - \n (dòng trắng)  - \d (đầu heading số như "10.1")  - [ (marker TABLE_START)
                .replaceAll("(?<!\\n)\\n(?![\\n\\d\\[])", " ")
                .replaceAll("\\.{2,}", ".")
                .trim();
    }

    /**
     * Mask nội dung bên trong [TABLE_START] ... [TABLE_END] để tránh regex heading match nhầm
     * các dòng trong bảng (table rows / numbered list bên trong table).
     *
     * Yêu cầu:
     * - Giữ nguyên độ dài string để matcher.start()/end() vẫn dùng được khi cắt pageText gốc.
     * - Giữ newline để không làm hỏng MULTILINE anchors.
     */
    private String maskTableBlocks(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder(text);

        int searchFrom = 0;
        while (searchFrom < sb.length()) {
            int start = sb.indexOf("[TABLE_START]", searchFrom);
            if (start < 0) break;
            int end = sb.indexOf("[TABLE_END]", start);
            if (end < 0) {
                end = sb.length();
            } else {
                end = Math.min(sb.length(), end + "[TABLE_END]".length());
            }

            for (int i = start; i < end; i++) {
                char c = sb.charAt(i);
                if (c != '\n') {
                    sb.setCharAt(i, ' ');
                }
            }

            searchFrom = end;
        }

        return sb.toString();
    }

    /**
     * Heuristic lọc false positives:
     * - Không coi table row / bullet / numbered list / footer-header như heading.
     */
    private boolean isLikelySectionHeader(String headerLine) {
        if (headerLine == null) return false;
        String h = headerLine.trim();
        if (h.isBlank()) return false;

        // Reject flow/instruction style lines often found in tables/steps (NOT headings)
        // e.g. "1. Login ... → ...", "6. Logout ... -> ..."
        if (h.contains("→") || h.contains("->") || h.contains("=>")) {
            return false;
        }

        // Reject markdown table rows or lines containing table delimiters
        if (h.contains("|")) return false;
        if (h.startsWith("[") || h.startsWith("]")) return false;

        // Reject bullet-like headings: "• xxx", "- xxx", "* xxx"
        if (h.matches("^(?:\\d+(?:\\.\\d+)*\\.?\\s+)?(?:[-*•]+)\\s+.*$")) return false;

        // Reject numbered list variants that often appear in content: "1) xxx", "1 - xxx"
        if (h.matches("^\\d+(?:\\.\\d+)*\\)?\\s*[-–—)]\\s+.*$")) return false;

        // Reject overly short "titles" (often data cells) after number
        String afterNumber = h.replaceFirst("^\\d+(?:\\.\\d+)*\\.?\\s+", "").trim();
        if (afterNumber.length() < 4) return false;

        // Reject "heading" that looks like a long sentence (common in table rows / list items)
        // Keep it conservative to avoid killing real headings.
        if (afterNumber.length() > 100) return false;

        // Reject footer/header artifacts
        String lower = afterNumber.toLowerCase();
        if (lower.matches(".*\\b(trang|page|tai lieu|tài liệu|noi bo|nội bộ|khong phat hanh|không phát hành)\\b.*")) {
            return false;
        }

        return true;
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
