package KLTN.RAG_CHATBOT_BE.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class tmp {
    
    // ✅ FIX 1: REGEX MỚI
    // - \\.?        : Cho phép có dấu chấm HOẶC không sau các con số (Xử lý "1." và "2.1")
    // - \\h+        : Horizontal whitespace (dấu cách, tab), KHÔNG match xuống dòng (\n) như \s
    // - (.+)        : Lấy toàn bộ chữ phía sau trên cùng 1 dòng
    private static final Pattern SECTION_HEADER_PATTERN = 
        Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?\\h+(.+)$", Pattern.MULTILINE);
    
    public List<Section> parseSections(String fullText, Map<Integer, String> pageContents) {
        List<Section> sections = new ArrayList<Section>();
        var matcher = SECTION_HEADER_PATTERN.matcher(fullText);
        
        String currentHeader = null;
        int currentStartPage = 1;
        int lastHeaderEndIndex = 0;

        while (matcher.find()) {
            if (currentHeader != null) {
                // Extract content từ END của header trước → START của header hiện tại
                String sectionText = fullText.substring(lastHeaderEndIndex, matcher.start()).trim();
                int endPage = estimatePageFromPosition(matcher.start(), pageContents);
                
                sections.add(new Section(currentHeader, currentStartPage, endPage, sectionText));
            }

            // Start new section
            currentHeader = matcher.group(0).trim(); // Full match: "2.1 Data Collection"
            currentStartPage = estimatePageFromPosition(matcher.start(), pageContents);
            lastHeaderEndIndex = matcher.end();
        }
        
        // Handle last section
        if (currentHeader != null) {
            String sectionText = fullText.substring(lastHeaderEndIndex).trim();
            // Lấy trang lớn nhất làm endPage cho section cuối
            int lastPage = Collections.max(pageContents.keySet());
            sections.add(new Section(currentHeader, currentStartPage, lastPage, sectionText));
        }

        sections = mergeSmallSections(sections, 1200); // Gộp các section nhỏ hơn 1200 ký tự
        
        return sections;
    }
    
    private int estimatePageFromPosition(int charPos, Map<Integer, String> pageContents) {
        int cumulative = 0;
        
        // ✅ FIX 2: Map không có thứ tự, bắt buộc phải SORT keys (1, 2, 3...) trước khi cộng dồn
        List<Integer> sortedPages = new ArrayList<>(pageContents.keySet());
        Collections.sort(sortedPages);
        
        for (int page : sortedPages) {
            cumulative += pageContents.get(page).length();
            if (charPos < cumulative) return page;
        }
        return sortedPages.get(sortedPages.size() - 1);
    }

    public List<Section> mergeSmallSections(List<Section> originalSections, int minCharLength) {
        if (originalSections == null || originalSections.isEmpty()) return new ArrayList<>();

        List<Section> mergedSections = new ArrayList<>();
        
        Section currentMerge = originalSections.get(0);

        for (int i = 1; i < originalSections.size(); i++) {
            Section nextSec = originalSections.get(i);

            // Nếu content hiện tại đang quá nhỏ, tiến hành GỘP (Merge) với section tiếp theo
            if (currentMerge.content().length() < minCharLength) {
                
                // Gộp Header: "2. Methodology & 2.1 Data Collection"
                String mergedHeader = currentMerge.header() + " & " + nextSec.header();
                
                // Gộp Content
                String mergedContent = currentMerge.content() + "\n\n" + 
                                       "[" + nextSec.header() + "]\n" + nextSec.content();
                
                currentMerge = new Section(
                    mergedHeader, 
                    currentMerge.startPage(), 
                    nextSec.endPage(), // Lấy trang kết thúc của section sau
                    mergedContent
                );
            } else {
                // Đủ lớn rồi thì lưu lại và bắt đầu gom chunk mới
                mergedSections.add(currentMerge);
                currentMerge = nextSec;
            }
        }
        
        // Đừng quên add chunk cuối cùng
        mergedSections.add(currentMerge);

        return mergedSections;
    }
    
    public static void main(String[] args) {
        String sampleText = """
        1. Introduction
        This is the introduction section.

        2. Methodology
        This section describes the methodology.

        2.1 Data Collection
        Details about data collection.

        2.2 Data Analysis
        Details about data analysis.

        3. Results
        This section presents the results.
        """;

        tmp parser = new tmp();
        List<Section> sections = parser.parseSections(sampleText, Map.of(
            1, "1. Introduction\nThis is the introduction section.\n",
            2, "2. Methodology\nThis section describes the methodology.\n2.1 Data Collection\nDetails about data collection.\n2.2 Data Analysis\nDetails about data analysis.\n",
            3, "3. Results\nThis section presents the results.\n"
        ));

        System.out.println("=== KẾT QUẢ SAU KHI FIX ===\n");
        for (int i = 0; i < sections.size(); i++) {
            Section sec = sections.get(i);
            System.out.println("[" + (i+1) + "] Header: " + sec.header());
            System.out.println("    Pages: " + sec.startPage() + "-" + sec.endPage());
            System.out.println("    Content: \"" + sec.content().replace("\n", "\\n") + "\"");
            System.out.println();
        }
    }
}


record Section(String header, int startPage, int endPage, String content) {}