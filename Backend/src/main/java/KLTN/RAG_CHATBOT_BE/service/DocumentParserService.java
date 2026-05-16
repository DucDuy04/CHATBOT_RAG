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
        Pattern.compile("(?m)^\\s{0,16}(\\d+(?:\\.\\d+)*)(?:\\.[ \\t]*|[ \\t]+)([^\\n]{3,160})$");

    /**
     * Markdown ATX: {@code #} … {@code ######}, khoảng trắng bắt buộc, rồi phần còn lại của dòng.
     * Group 1 = tiêu đề hiển thị (không gồm prefix {@code #}), ví dụ {@code ## 2. Chính sách…} → {@code 2. Chính sách…}.
     */
    private static final Pattern MARKDOWN_ATX_HEADER_LINE_PATTERN =
            Pattern.compile("(?m)^\\s{0,16}#{1,6}\\s+(.+)$");

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

                // 2. Tabula extraction — chạy TRƯỚC để quyết định có cần EarlyDetect không
                List<Table> tabulaTables = new java.util.ArrayList<>();
                boolean tabulaSpreadsheetFound = false;
                long usableFromSpreadsheet = 0;
                try {
                    Page page = extractor.extract(pageNum);

                    // Ưu tiên SpreadsheetExtractionAlgorithm (bảng có đường kẻ/ruling lines)
                    List<Table> spreadsheetTables = sea.extract(page);
                    tabulaSpreadsheetFound = !spreadsheetTables.isEmpty();
                    tabulaTables.addAll(spreadsheetTables);

                    // Đếm số bảng usable từ SpreadsheetAlgo (quick pre-check, không log)
                    if (tabulaSpreadsheetFound) {
                        for (Table t : spreadsheetTables) {
                            String md = convertTableToMarkdown(t, new String[]{""});
                            if (isUsableTable(t, md)) {
                                usableFromSpreadsheet++;
                                break; // Chỉ cần 1 usable là đủ để giữ SpreadsheetAlgo result
                            }
                        }
                        if (usableFromSpreadsheet == 0) {
                            log.info("[Parse] Page {}: SpreadsheetAlgo tìm {} tables nhưng 0 usable " +
                                     "(có thể là micro-table hoặc corrupted) — thử BasicAlgo fallback",
                                     pageNum, tabulaTables.size());
                        }
                    }

                    // Fallback sang BasicAlgo khi SpreadsheetAlgo: không tìm được gì, HOẶC tìm được nhưng 0 usable
                    if (!tabulaSpreadsheetFound || usableFromSpreadsheet == 0) {
                        technology.tabula.extractors.BasicExtractionAlgorithm bea =
                                new technology.tabula.extractors.BasicExtractionAlgorithm();
                        List<Table> basicTables = bea.extract(page);
                        if (!basicTables.isEmpty()) {
                            log.debug("[Parse] Page {}: BasicAlgo={} tables (fallback từ SpreadsheetAlgo={} tables, 0 usable)",
                                    pageNum, basicTables.size(), tabulaTables.size());
                            tabulaTables.clear();
                            tabulaTables.addAll(basicTables);
                        }
                    }
                } catch (Exception e) {
                    log.debug("[Parse] Page {}: table extraction skipped — {}", pageNum, e.getMessage());
                }

                // 3. Append text — EarlyDetect chỉ khi SpreadsheetAlgo không có usable table
                String processedText = (usableFromSpreadsheet > 0)
                        ? pageText
                        : detectTablesInRawText(pageText, pageNum);
                pageBuilder.append(cleanText(processedText)).append("\n");

                // 4. Append Tabula tables vào pageBuilder
                for (Table table : tabulaTables) {
                    String headerBeforeConvert = currentHeader[0];
                    boolean continuationByData = isContinuationTable(table, headerBeforeConvert);
                    boolean continuationByRepeatedHeader =
                            isRepeatedHeaderContinuationTable(table, headerBeforeConvert);
                    boolean canMergeToPrev =
                            (continuationByData || continuationByRepeatedHeader)
                                    && pageNum > 1
                                    && lastTableHeaderPage == (pageNum - 1)
                                    && pageContents.containsKey(pageNum - 1);

                    if (canMergeToPrev) {
                        String rowsOnly = continuationByRepeatedHeader
                                ? convertTableDataRowsOnly(table, 1)
                                : removeMarkdownHeader(
                                        convertTableToMarkdown(table, currentHeader),
                                        headerBeforeConvert);
                        if (rowsOnly != null && !rowsOnly.isBlank()) {
                            String prev = pageContents.get(pageNum - 1);
                            pageContents.put(pageNum - 1, appendRowsIntoLastTable(prev, rowsOnly));
                            log.info(
                                    "[Parse] Table MERGED continuation page={} → page={}: mode={} rows={}",
                                    pageNum,
                                    pageNum - 1,
                                    continuationByRepeatedHeader ? "REPEATED_HEADER" : "DATA_ROWS",
                                    table.getRows() == null ? 0 : table.getRows().size());
                        }
                        continue;
                    }

                    String tableMarkdown = convertTableToMarkdown(table, currentHeader);
                    if (!isUsableTable(table, tableMarkdown)) {
                        logRejectedTable(table, tableMarkdown, pageNum);
                        continue;
                    }
                    int acceptedRows = table.getRows() == null ? 0 : table.getRows().size();
                    int acceptedCols = table.getRows() == null || table.getRows().isEmpty() ? 0
                            : table.getRows().stream().mapToInt(List::size).max().orElse(0);
                    String acceptedPreview = tableMarkdown.length() > 200
                            ? tableMarkdown.substring(0, 200).replace("\n", "↵") + "…"
                            : tableMarkdown.replace("\n", "↵");
                    log.info("[Parse] Table ACCEPTED page={}: rows={} cols={} preview='{}'",
                            pageNum, acceptedRows, acceptedCols, acceptedPreview);
                    pageBuilder.append("\n[TABLE_START]\n");
                    pageBuilder.append(tableMarkdown);
                    pageBuilder.append("[TABLE_END]\n");
                    lastTableHeader = currentHeader[0];
                    lastTableHeaderPage = pageNum;
                }

                if (!tabulaTables.isEmpty()) {
                    log.debug("[Parse] Page {}: {} table(s) extracted (spreadsheet={})",
                            pageNum, tabulaTables.size(), tabulaSpreadsheetFound);
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

        boolean markdownHeadingMode = detectMarkdownHeadingMode(sortedPages);

        // Mặc định "General" cho phần intro trước heading đầu tiên
        String currentHeader = "General";
        StringBuilder currentContent = new StringBuilder();
        int startPage = sortedPages.isEmpty() ? 1 : sortedPages.firstKey();
        int sectionOrder = 0;
        boolean firstHeadingFound = false;

        java.util.Map<String, Integer> seenSectionNumberCounts = new java.util.HashMap<>();
        String currentSectionNumber = null;

        // --- Counters cho logging ---
        int totalCandidates = 0;
        int acceptedHeadings = 0;

        for (Map.Entry<Integer, String> entry : sortedPages.entrySet()) {
            int currentPage = entry.getKey();
            String pageText = entry.getValue();

            // Mask nội dung bảng để regex heading không match bên trong table rows.
            String maskedForHeadingScan = maskTableBlocks(pageText);
            Matcher matcher = markdownHeadingMode
                    ? MARKDOWN_ATX_HEADER_LINE_PATTERN.matcher(maskedForHeadingScan)
                    : SECTION_HEADER_PATTERN.matcher(maskedForHeadingScan);
            int lastEndIndex = 0;

            while (matcher.find()) {
                totalCandidates++;
                String candidateHeader = markdownHeadingMode
                        ? safeTrim(matcher.group(1))
                        : matcher.group(0).trim();
                if (markdownHeadingMode && candidateHeader.isEmpty()) {
                    continue;
                }
                String candidateNumber = extractSectionNumber(candidateHeader);

                // --- Bước 1: false-positive filter (với reason log) ---
                String skipReason = headingSkipReason(
                        candidateHeader, candidateNumber,
                        currentHeader, currentSectionNumber,
                        matcher.start(), seenSectionNumberCounts,
                        markdownHeadingMode);

                if (skipReason != null) {
                    log.debug("[Parse] SKIP [{}] page={} header='{}'", skipReason, currentPage, candidateHeader);
                    continue;
                }

                // --- Bước 2: Heading được chấp nhận ---
                acceptedHeadings++;
                log.debug("[Parse] ACCEPT heading #{}: page={} header='{}' number='{}'",
                        acceptedHeadings, currentPage, candidateHeader, candidateNumber);

                // --- Bước 3: Lưu section đang xử lý trước khi mở section mới ---
                currentContent.append(pageText, lastEndIndex, matcher.start());
                String finalizedContent = currentContent.toString().trim();

                // Lưu section trước nếu:
                //  - Là heading thật (không phải "General" mặc định trước heading đầu tiên), HOẶC
                //  - Là "General" nhưng có content (intro text trước heading đầu tiên)
                boolean isPrevDefaultEmpty = "General".equals(currentHeader) && !firstHeadingFound && finalizedContent.isEmpty();
                if (!isPrevDefaultEmpty) {
                    int prevLevel = computeHeadingLevel(currentSectionNumber);
                    Section saved = new Section(currentHeader, startPage, currentPage, finalizedContent, sectionOrder, prevLevel);
                    sections.add(saved);
                    log.debug("[Parse] Section saved: order={} header='{}' pages={}-{} contentLen={}",
                            sectionOrder, currentHeader, startPage, currentPage, finalizedContent.length());
                    sectionOrder++;
                }

                // --- Bước 4: Chuyển sang section mới ---
                firstHeadingFound = true;
                currentContent.setLength(0);
                currentHeader = candidateHeader;
                startPage = currentPage;
                lastEndIndex = matcher.end();

                if (candidateNumber != null) {
                    seenSectionNumberCounts.merge(candidateNumber, 1, Integer::sum);
                    currentSectionNumber = candidateNumber;
                }
            }

            // Nội dung còn lại của trang (sau heading cuối trên trang này)
            currentContent.append(pageText, lastEndIndex, pageText.length()).append("\n");
        }

        // --- Lưu section cuối cùng ---
        String lastContent = currentContent.toString().trim();
        // Lưu nếu có heading được detect HOẶC file không có heading (document toàn text)
        if (firstHeadingFound || !lastContent.isEmpty()) {
            int lastLevel = computeHeadingLevel(currentSectionNumber);
            Section last = new Section(currentHeader, startPage,
                    sortedPages.isEmpty() ? startPage : sortedPages.lastKey(),
                    lastContent, sectionOrder, lastLevel);
            sections.add(last);
            log.debug("[Parse] Section saved (last): order={} header='{}' pages={}-{} contentLen={}",
                    sectionOrder, currentHeader, startPage, last.endPage(), lastContent.length());
        }

        // --- Summary log ---
        log.info("[Parse] Heading mode: markdownAtx={} totalCandidates={} accepted={} skipped={}",
                markdownHeadingMode, totalCandidates, acceptedHeadings, totalCandidates - acceptedHeadings);
        log.info("[Parse] Sections created: {} (no merging applied)", sections.size());

        if (sections.isEmpty() && !pageContents.isEmpty()) {
            log.warn("[Parse] WARNING: 0 sections created from non-empty document. Check heading detection.");
        }

        return sections;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Bật chế độ tách section theo markdown ATX ({@code #}…{@code ######}) khi toàn bộ document
     * có ít nhất một dòng heading markdown hợp lệ — tránh TXT có cấu trúc markdown lại bị
     * regex số {@code 1.}… tách nhầm list thành section root.
     */
    private boolean detectMarkdownHeadingMode(TreeMap<Integer, String> sortedPages) {
        for (String pageText : sortedPages.values()) {
            if (pageText == null || pageText.isBlank()) {
                continue;
            }
            String masked = maskTableBlocks(pageText);
            if (MARKDOWN_ATX_HEADER_LINE_PATTERN.matcher(masked).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra xem heading candidate có nên bị bỏ qua không.
     * Trả về null nếu heading hợp lệ, trả về chuỗi mô tả lý do nếu phải bỏ qua.
     *
     * Ưu tiên GIỮ LẠI nếu không chắc chắn là false positive.
     */
    private String headingSkipReason(
            String candidateHeader,
            String candidateNumber,
            String currentHeader,
            String currentSectionNumber,
            int matchStart,
            java.util.Map<String, Integer> seenSectionNumberCounts,
            boolean markdownHeadingMode) {

        // 1. Kiểm tra false positive cơ bản (format/content)
        String fpReason = isLikelySectionHeaderSkipReason(candidateHeader, markdownHeadingMode);
        if (fpReason != null) return "false-positive:" + fpReason;

        // 2. Header lặp lại chính xác → là page header/footer lặp
        if (candidateHeader.equals(currentHeader)) return "repeated-exact-header";

        if (candidateNumber != null) {
            // 3. Số section đã xuất hiện TRƯỚC và lại xuất hiện gần đầu trang → khả năng header/footer lặp
            int count = seenSectionNumberCounts.getOrDefault(candidateNumber, 0);
            boolean nearTopOfPage = matchStart <= 320;
            if (count > 0 && nearTopOfPage) return "repeated-number-near-page-top";

            // 4. Heading không nhất quán về số thứ tự (vd step "1." xuất hiện sau section "6.4")
            if (!isStructurallyConsistentHeading(currentSectionNumber, candidateNumber)) {
                return "structurally-inconsistent(current=" + currentSectionNumber + ",candidate=" + candidateNumber + ")";
            }
        }

        return null; // heading hợp lệ
    }

    /**
     * Kiểm tra false-positive thuần dựa trên format/nội dung dòng.
     * Trả về null nếu hợp lệ, trả về lý do nếu là false positive.
     *
     * @param markdownHeadingMode khi true (ATX), outline số {@code N. …} tin cậy hơn — cho phép mục 7 chứa
     *        "tài liệu" hợp lệ; vẫn skip dòng chỉ có "tài liệu" kiểu preamble không có số mục (vd tiêu đề sau {@code #}).
     */
    private String isLikelySectionHeaderSkipReason(String headerLine, boolean markdownHeadingMode) {
        if (headerLine == null) return "null";
        String h = headerLine.trim();
        if (h.isBlank()) return "blank";

        // Flow/instruction: "1. Login → System", "6. Logout -> Done"
        if (h.contains("→") || h.contains("->") || h.contains("=>")) return "flow-arrow";

        // Markdown table row hoặc marker
        if (h.contains("|")) return "contains-pipe";
        if (h.startsWith("[") || h.startsWith("]")) return "bracket-prefix";

        // Bullet list: "• xxx", "- xxx", "* xxx" sau số
        if (h.matches("^(?:\\d+(?:\\.\\d+)*\\.?\\s+)?(?:[-*•]+)\\s+.*$")) return "bullet-list";

        // Numbered list variant: "1) xxx", "1 - xxx"
        if (h.matches("^\\d+(?:\\.\\d+)*\\)?\\s*[-–—)]\\s+.*$")) return "numbered-list-variant";

        // Title quá ngắn sau số (likely data cell, không phải tiêu đề)
        String afterNumber = h.replaceFirst("^\\d+(?:\\.\\d+)*\\.?\\s+", "").trim();
        if (afterNumber.length() < 3) return "title-too-short(<3)";

        // Footer/header artifacts (cụm từ đặc trưng).
        // 21D2: "tài liệu" trong tiêu đề outline ATX số "7. … trong tài liệu" là hợp lệ (21E thiếu section 7
        // do skip nhầm). Với markdown ATX, chỉ bỏ qua heuristic "tài liệu" khi dòng là outline số N.
        String lower = afterNumber.toLowerCase();
        boolean strongFooter = lower.matches(
                ".*\\b(trang|page|noi bo|nội bộ|khong phat hanh|không phát hành)\\b.*");
        boolean taiLieuToken = lower.matches(".*\\b(tai lieu|tài liệu)\\b.*");
        boolean numberedOutline = h.matches("^\\d+(?:\\.\\d+)*\\.?\\s+.+");
        if (strongFooter) {
            return "footer-header-artifact";
        }
        if (taiLieuToken) {
            if (markdownHeadingMode && numberedOutline) {
                return null;
            }
            return "footer-header-artifact";
        }

        return null; // hợp lệ
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
    /**
     * Gộp các section nhỏ lại với nhau.
     * NOTE: Hàm này KHÔNG được gọi trong pipeline chính nữa — mỗi heading hợp lệ phải
     * là một section riêng bất kể độ dài content.
     * Giữ lại hàm này chỉ để dùng trong test/thử nghiệm nếu cần.
     */
    public List<Section> mergeSmallSections(List<Section> originalSections, int minCharLength) {
        if (originalSections == null || originalSections.isEmpty()) return new ArrayList<>();

        List<Section> mergedSections = new ArrayList<>();
        Section currentMerge = originalSections.get(0);

        for (int i = 1; i < originalSections.size(); i++) {
            Section nextSec = originalSections.get(i);

            boolean tooSmall = currentMerge.content().length() < minCharLength;
            boolean sameParent = isSameParentSection(currentMerge.header(), nextSec.header());
            boolean isParentChild = isParentOf(currentMerge.header(), nextSec.header());

            if (tooSmall && sameParent && !isParentChild) {
                String mergedContent = currentMerge.content()
                        + "\n\n[" + nextSec.header() + "]\n" + nextSec.content();
                // Giữ orderIndex và headingLevel của section đầu khi gộp
                currentMerge = new Section(currentMerge.header(), currentMerge.startPage(),
                        nextSec.endPage(), mergedContent, currentMerge.orderIndex(),
                        currentMerge.headingLevel());
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

    /**
     * Log chi tiết bảng bị reject để hỗ trợ debug và fallback pseudo-table detection.
     * Ghi ra: lý do reject, số row/col, preview markdown (tối đa 200 ký tự).
     */
    private void logRejectedTable(Table table, String markdown, int pageNum) {
        if (table == null) {
            log.info("[Parser] Table REJECTED page={}: reason=null-table", pageNum);
            return;
        }
        String reason = getTableRejectionReason(table, markdown);
        int rows = table.getRows() == null ? 0 : table.getRows().size();
        int cols = table.getRows() == null || table.getRows().isEmpty() ? 0
                : table.getRows().stream().mapToInt(List::size).max().orElse(0);
        String preview = (markdown == null || markdown.isBlank()) ? "(empty)"
                : markdown.length() > 200 ? markdown.substring(0, 200).replace("\n", "↵") + "…"
                : markdown.replace("\n", "↵");
        log.info("[Parser] Table REJECTED page={}: reason='{}' rows={} cols={} preview='{}'",
                pageNum, reason, rows, cols, preview);
    }

    /**
     * Tính lý do reject của bảng (không thay đổi isUsableTable để tránh side-effects).
     * Trả về chuỗi mô tả lý do đầu tiên tìm được.
     */
    private String getTableRejectionReason(Table table, String markdown) {
        if (table == null || markdown == null || markdown.isBlank()) return "null-or-blank-markdown";

        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows == null || rows.size() < 3) {
            return "too-few-rows(" + (rows == null ? 0 : rows.size()) + "<3)";
        }

        int maxColumns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (maxColumns < 2) return "too-few-columns(" + maxColumns + "<2)";

        int nonEmptyCells = 0, emptyCells = 0, textChars = 0, totalCells = 0;
        for (List<RectangularTextContainer> row : rows) {
            for (RectangularTextContainer cell : row) {
                String text = cell.getText() == null ? "" : cell.getText().trim();
                totalCells++;
                if (!text.isBlank()) { nonEmptyCells++; textChars += text.length(); }
                else emptyCells++;
            }
        }

        if (totalCells > 0 && (double) emptyCells / totalCells > 0.6) {
            return "too-many-empty-cells(" + emptyCells + "/" + totalCells + ">"
                    + String.format("%.0f", (double) emptyCells / totalCells * 100) + "%)";
        }

        List<RectangularTextContainer> headerRow = rows.get(0);
        long headerNonEmpty = headerRow.stream()
                .filter(c -> c.getText() != null && !c.getText().trim().isBlank()).count();
        if (headerNonEmpty < 2) {
            return "header-row-too-sparse(nonEmpty=" + headerNonEmpty + "<2)";
        }

        if (nonEmptyCells < 4) return "too-few-non-empty-cells(" + nonEmptyCells + "<4)";
        if (textChars < 40) return "too-little-text(" + textChars + "<40chars)";

        return "unknown";
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
        if (maxColumns < 2) {
            return false;
        }

        int nonEmptyCells = 0;
        int textChars = 0;
        int emptyCells = 0;
        int totalCells = 0;
        for (List<RectangularTextContainer> row : rows) {
            for (RectangularTextContainer cell : row) {
                String text = cell.getText() == null ? "" : cell.getText().trim();
                totalCells++;
                if (!text.isBlank()) {
                    nonEmptyCells++;
                    textChars += text.length();
                } else {
                    emptyCells++;
                }
            }
        }

        // Bảng có quá nhiều cell rỗng (>60%) → không đáng tin cậy
        if (totalCells > 0 && (double) emptyCells / totalCells > 0.6) {
            log.debug("[Parser] Table rejected: too many empty cells ({}/{})", emptyCells, totalCells);
            return false;
        }

        // Kiểm tra header row: các cell header không được toàn rỗng
        List<RectangularTextContainer> headerRow = rows.get(0);
        long headerNonEmpty = headerRow.stream()
                .filter(c -> c.getText() != null && !c.getText().trim().isBlank())
                .count();
        if (headerNonEmpty < 2) {
            log.debug("[Parser] Table rejected: header row has too few non-empty cells ({})", headerNonEmpty);
            return false;
        }

        return nonEmptyCells >= 4 && textChars >= 40;
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

    /**
     * Trang sau của PDF thường lặp lại header row (Thực thể | Thuộc tính) thay vì chỉ data rows.
     * Trường hợp này không pass {@link #isContinuationTable} vì firstRow không phải data row.
     */
    private boolean isRepeatedHeaderContinuationTable(Table table, String existingHeaderMarkdown) {
        if (table == null || existingHeaderMarkdown == null || existingHeaderMarkdown.isBlank()) {
            return false;
        }
        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows == null || rows.size() < 2) {
            return false;
        }
        List<RectangularTextContainer> firstRow = rows.get(0);
        if (isDataRow(firstRow)) {
            return false;
        }
        String existingHeaderRow = extractMarkdownHeaderRow(existingHeaderMarkdown);
        String candidateHeaderRow = buildMarkdownHeaderRowLine(firstRow);
        return normalizeHeaderRow(existingHeaderRow).equals(normalizeHeaderRow(candidateHeaderRow));
    }

    private String extractMarkdownHeaderRow(String headerMarkdown) {
        if (headerMarkdown == null || headerMarkdown.isBlank()) {
            return "";
        }
        int newline = headerMarkdown.indexOf('\n');
        return (newline >= 0 ? headerMarkdown.substring(0, newline) : headerMarkdown).trim();
    }

    private String buildMarkdownHeaderRowLine(List<RectangularTextContainer> row) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("| ");
        for (RectangularTextContainer cell : row) {
            String text = cell.getText() == null ? "" : cell.getText().trim().replace("\n", " ");
            sb.append(text).append(" | ");
        }
        return sb.toString().trim();
    }

    private static String normalizeHeaderRow(String row) {
        if (row == null) {
            return "";
        }
        return row.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    /** Markdown rows only (no header/separator), used when merging repeated-header continuation. */
    private String convertTableDataRowsOnly(Table table, int startRowIndex) {
        if (table == null || table.getRows() == null) {
            return "";
        }
        List<List<RectangularTextContainer>> rows = table.getRows();
        if (startRowIndex >= rows.size()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startRowIndex; i < rows.size(); i++) {
            sb.append("| ");
            for (RectangularTextContainer cell : rows.get(i)) {
                String text = cell.getText() == null ? "" : cell.getText().trim().replace("\n", " ");
                sb.append(text).append(" | ");
            }
            sb.append("\n");
        }
        return sb.toString();
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

    private static final java.util.regex.Pattern EARLY_HEADING_PATTERN =
            java.util.regex.Pattern.compile("^\\d+(\\.\\d+)*\\.?\\s+.{3,}$");

    /**
     * Phát hiện pseudo-table trong raw text (trước cleanText) và convert sang Markdown.
     * Chỉ chạy khi Tabula SpreadsheetAlgo không tìm được usable table.
     *
     * Tiêu chí nhận biết pseudo-table line:
     *  - 3+ cột khi split bởi \s{2,}
     *  - Không phải heading số (^\d+(\.\d+)*\.?\s+.{3,})
     *  - Không phải bullet (•, -, *)
     *
     * Block hợp lệ: >= 3 dòng liên tiếp, >= 70% dòng có số cột trong [headerCols±1].
     */
    private String detectTablesInRawText(String rawText, int pageNum) {
        if (rawText == null || rawText.isBlank()) return rawText == null ? "" : rawText;

        String[] lines = rawText.split("\n", -1);
        int n = lines.length;
        boolean[] isPseudo = new boolean[n];

        for (int i = 0; i < n; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*")) continue;
            if (trimmed.startsWith("[TABLE_START]") || trimmed.startsWith("[TABLE_END]")) continue;
            if (EARLY_HEADING_PATTERN.matcher(trimmed).matches()) continue;
            if (trimmed.split("\\s{2,}").length >= 3) {
                isPseudo[i] = true;
            }
        }

        // Gom block liên tiếp >= 3 dòng
        java.util.List<int[]> blocks = new java.util.ArrayList<>();
        int i = 0;
        while (i < n) {
            if (isPseudo[i]) {
                int start = i;
                while (i < n && isPseudo[i]) i++;
                if (i - start >= 3) blocks.add(new int[]{start, i - 1});
            } else {
                i++;
            }
        }
        if (blocks.isEmpty()) return rawText;

        // Lọc theo column consistency >= 70%
        java.util.List<int[]> validBlocks = new java.util.ArrayList<>();
        for (int[] block : blocks) {
            int headerCols = lines[block[0]].trim().split("\\s{2,}").length;
            int blockLen = block[1] - block[0] + 1;
            int consistent = 0;
            for (int j = block[0]; j <= block[1]; j++) {
                int c = lines[j].trim().split("\\s{2,}").length;
                if (c >= headerCols - 1 && c <= headerCols + 1) consistent++;
            }
            if ((double) consistent / blockLen >= 0.7) validBlocks.add(block);
        }
        if (validBlocks.isEmpty()) return rawText;

        log.info("[Parse] Page {}: EarlyDetect tìm {} table block(s) trong raw text",
                pageNum, validBlocks.size());

        // Build markdown cho từng block và đánh dấu dòng thuộc block
        boolean[] inBlock = new boolean[n];
        java.util.Map<Integer, String> blockMarkdownMap = new java.util.LinkedHashMap<>();
        int tableIdx = 0;
        for (int[] block : validBlocks) {
            int headerCols = lines[block[0]].trim().split("\\s{2,}").length;
            String[] headerParts = lines[block[0]].trim().split("\\s{2,}", headerCols);
            int dataRows = block[1] - block[0];

            StringBuilder md = new StringBuilder();
            md.append("|");
            for (String col : headerParts) md.append(" ").append(col.trim()).append(" |");
            md.append("\n|");
            for (int k = 0; k < headerCols; k++) md.append(" --- |");
            md.append("\n");
            for (int j = block[0] + 1; j <= block[1]; j++) {
                String[] cells = lines[j].trim().split("\\s{2,}", headerCols);
                md.append("|");
                for (int k = 0; k < headerCols; k++) {
                    String val = k < cells.length ? cells[k].trim() : "";
                    md.append(" ").append(val).append(" |");
                }
                md.append("\n");
            }

            log.info("[Parse] Page {}: EarlyDetect table block {} — {} rows, {} cols",
                    pageNum, tableIdx, dataRows, headerCols);

            blockMarkdownMap.put(block[0], "\n[TABLE_START]\n" + md + "[TABLE_END]\n");
            for (int j = block[0]; j <= block[1]; j++) inBlock[j] = true;
            tableIdx++;
        }

        // Rebuild text — thay block gốc bằng markdown
        StringBuilder result = new StringBuilder();
        for (int j = 0; j < n; j++) {
            if (inBlock[j]) {
                if (blockMarkdownMap.containsKey(j)) result.append(blockMarkdownMap.get(j));
            } else {
                result.append(lines[j]).append("\n");
            }
        }
        return result.toString();
    }

    private static final java.util.regex.Pattern PAGE_HEADER_FOOTER_PATTERN =
            java.util.regex.Pattern.compile(
                    "(?i)^\\s*(?:" +
                    "trang\\s+\\d+\\s*/\\s*\\d+" +        // "Trang X / Y" hoặc "Trang X/Y"
                    "|page\\s+\\d+\\s*(?:of|/)\\s*\\d+" + // "Page X of Y" hoặc "Page X/Y"
                    "|trang\\s+\\d+.*\\|.*"                // footer dạng "Trang N | ..." (toàn dòng)
                    + ")\\s*$"
            );

    private static final java.util.regex.Pattern TABLE_BLOCK_PATTERN =
            java.util.regex.Pattern.compile("\\[TABLE_START].*?\\[TABLE_END]",
                    java.util.regex.Pattern.DOTALL);

    private String cleanText(String text) {
        if (text == null) return "";

        // Tách [TABLE_START]...[TABLE_END] ra ngoài trước khi normalize để tránh bị corrupt
        java.util.List<String> tableBlocks = new java.util.ArrayList<>();
        java.util.regex.Matcher tblMatcher = TABLE_BLOCK_PATTERN.matcher(text);
        StringBuffer tblBuf = new StringBuffer();
        while (tblMatcher.find()) {
            tableBlocks.add(tblMatcher.group());
            // Bọc placeholder bằng \n\n để newline xung quanh không bị gộp bởi regex sau
            tblMatcher.appendReplacement(tblBuf,
                    "\n\n[__TBLP_" + (tableBlocks.size() - 1) + "__]\n\n");
        }
        tblMatcher.appendTail(tblBuf);
        String working = tblBuf.toString();

        String normalized = working
                .replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ')
                .replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "")
                .replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n");

        // Lọc các dòng header/footer phân trang (giữ lại dòng placeholder)
        String[] lines = normalized.split("\n", -1);
        int removedCount = 0;
        java.util.List<String> filtered = new java.util.ArrayList<>(lines.length);
        for (String line : lines) {
            if (line.contains("[__TBLP_")) {
                filtered.add(line);
            } else if (PAGE_HEADER_FOOTER_PATTERN.matcher(line).matches()) {
                removedCount++;
            } else {
                filtered.add(line);
            }
        }
        if (removedCount > 0) {
            log.debug("[cleanText] Đã xóa {} dòng header/footer phân trang", removedCount);
        }

        String afterNormalize = String.join("\n", filtered)
                .replaceAll("\\n{3,}", "\n\n")
                // Giữ nguyên xuống dòng SAU heading để tránh dính heading với paragraph.
                .replaceAll(
                        "(?m)^(\\s{0,16}\\d+(?:\\.\\d+)*\\.?\\s+[^\\n]{3,160})\\n(?!\\n)",
                        "$1\n\n"
                )
                // Giữ newline trước markdown ATX heading (tránh dính "##" vào cuối câu trước).
                .replaceAll(
                        "(?m)^(\\s{0,16}#{1,6}\\s+[^\\n]+)\\n(?!\\n)",
                        "$1\n\n"
                );

        // Restore table blocks TRƯỚC bước newline-to-space để bảo toàn cấu trúc markdown
        for (int i = 0; i < tableBlocks.size(); i++) {
            afterNormalize = afterNormalize.replace("[__TBLP_" + i + "__]", tableBlocks.get(i));
        }

        // Gộp newline thành space — loại trừ thêm '|' và dòng bắt đầu bằng markdown ATX (# …)
        // để heading markdown vẫn bắt đầu dòng mới sau cleanText (TXT golden + hybrid PDF hiếm).
        return afterNormalize
                .replaceAll("(?<!\\n)\\n(?![\\n\\d\\[|])(?!\\s{0,16}#{1,6}\\s)", " ")
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


    // ==========================================
    // 6. SECTION UTILITY
    // ==========================================

    /**
     * Tính cấp độ heading từ số section.
     * "1" → 1, "1.2" → 2, "1.2.3" → 3, null/"General" → 0
     */
    public int computeHeadingLevel(String sectionNumber) {
        if (sectionNumber == null || sectionNumber.isBlank()) return 0;
        return sectionNumber.split("\\.").length;
    }

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
