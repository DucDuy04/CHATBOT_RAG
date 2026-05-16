package KLTN.RAG_CHATBOT_BE;

import KLTN.RAG_CHATBOT_BE.record.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.record.Section;
import KLTN.RAG_CHATBOT_BE.service.ChunkingService2;
import KLTN.RAG_CHATBOT_BE.service.DocumentParserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class ParserAndOrderingTests {

    // ═══════════════════════════════════════════════════════════════════
    // EXISTING TESTS (regression prevention)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Golden TXT (markdown): phải tách theo {@code ##} chứ không tạo section root từ list {@code 1.}/{@code 2.}/{@code 3.}
     * trong mục chính sách; nội dung "Bước 2" nằm trong section quy trình.
     */
    @Test
    void markdown_golden_txt_sections_follow_atx_headings_not_numbered_lists() throws Exception {
        DocumentParserService parser = new DocumentParserService();
        Path golden = Path.of(System.getProperty("user.dir"))
                .resolve("../docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt")
                .normalize();
        if (!Files.isRegularFile(golden)) {
            // cwd có thể là repo root khi chạy IDE khác Maven
            golden = Path.of(System.getProperty("user.dir"))
                    .resolve("docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt")
                    .normalize();
        }
        assumeThat(golden)
                .as("Cần file golden tại docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt")
                .exists();

        byte[] bytes = Files.readAllBytes(golden);
        var file = new MockMultipartFile(
                "files", "RAG_GOLDEN_TEST_DOCUMENT.txt", "text/plain", bytes);

        List<Section> sections = parser.parse(file);

        assertThat(sections.get(0).header())
                .as("Intro trước ## 1 vẫn là General (# tiêu đề không tách section)")
                .isEqualTo("General");

        assertThat(sections.stream().map(Section::header))
                .noneMatch(h -> h.startsWith("1. Phản hồi yêu cầu trong"))
                .noneMatch(h -> h.startsWith("2. Mỗi phiên chat hỗ trợ"))
                .noneMatch(h -> h.startsWith("3. Không hỗ trợ can thiệp"));

        Section quyTrinh = sections.stream()
                .filter(s -> s.header().contains("Quy trình"))
                .findFirst()
                .orElse(null);
        assertThat(quyTrinh).as("Phải có section mục Quy trình từ ## 3.").isNotNull();
        assertThat(quyTrinh.content()).contains("Bước 2");

        Section bangGoi = sections.stream()
                .filter(s -> s.header().contains("Bảng gói dịch vụ"))
                .findFirst()
                .orElse(null);
        assertThat(bangGoi).as("Phải có section mục bảng từ ## 4.").isNotNull();
        assertThat(bangGoi.content()).contains("| Gói |").contains("Basic");

        Section chinhSach = sections.stream()
                .filter(s -> s.header().contains("Chính sách hỗ trợ"))
                .findFirst()
                .orElse(null);
        assertThat(chinhSach).isNotNull();
        assertThat(chinhSach.content()).contains("24 giờ làm việc").contains("15 phút");

        assertThat(sections.stream().map(Section::header))
                .as("Phải có section riêng cho ## 7 (không gộp vào mục 6)")
                .anyMatch(h -> h.contains("Phạm vi") && h.contains("KHÔNG có trong tài liệu"));

        ChunkingService2 chunker = new ChunkingService2();
        List<DocumentChunk> chunks = chunker.processSections2(sections);
        DocumentChunk buoc2Chunk = chunks.stream()
                .filter(c -> "text".equals(c.chunkType()) && c.content().contains("Bước 2"))
                .findFirst()
                .orElse(null);
        assertThat(buoc2Chunk).as("Chunk text chứa Bước 2 phải gắn header section Quy trình").isNotNull();
        assertThat(buoc2Chunk.header()).contains("Quy trình");

        boolean tableInBangGoi = chunks.stream()
                .anyMatch(c -> ("table_summary".equals(c.chunkType()) || "table_row_group".equals(c.chunkType()))
                        && c.header() != null && c.header().contains("Bảng gói dịch vụ"));
        assertThat(tableInBangGoi).as("Table chunk phải thuộc section Bảng gói dịch vụ").isTrue();

        DocumentChunk outOfScopeChunk = chunks.stream()
                .filter(c -> c.content().contains("USD/VND"))
                .findFirst()
                .orElse(null);
        assertThat(outOfScopeChunk).as("Chunk mục out-of-scope phải gắn header Phạm vi…").isNotNull();
        assertThat(outOfScopeChunk.header()).contains("Phạm vi").contains("KHÔNG có trong tài liệu");
    }

    @Test
    void each_valid_heading_must_produce_its_own_section_no_merging() {
        DocumentParserService parser = new DocumentParserService();
        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(1, """
                1. Alpha
                Intro A
                1.1 Alpha One
                Content 1.1
                1.2 Alpha Two
                1.3 Alpha Three
                Content 1.3
                2. Beta
                """);

        List<Section> sections = parser.parseSections(pages);

        assertThat(sections).hasSizeGreaterThanOrEqualTo(5);

        Section sAlpha = sections.get(0);
        Section s11    = sections.get(1);
        Section s12    = sections.get(2);
        Section s13    = sections.get(3);

        assertThat(sAlpha.header()).contains("Alpha");
        assertThat(sAlpha.content()).contains("Intro A");
        assertThat(sAlpha.content()).doesNotContain("Content 1.1");

        assertThat(s11.header()).contains("1.1");
        assertThat(s11.content()).contains("Content 1.1");
        assertThat(s12.header()).contains("1.2");
        assertThat(s13.header()).contains("1.3");
        assertThat(s13.content()).contains("Content 1.3");

        for (int i = 1; i < sections.size(); i++) {
            assertThat(sections.get(i).orderIndex()).isGreaterThan(sections.get(i - 1).orderIndex());
        }
    }

    @Test
    void chunks_should_have_sectionOrder_chunkOrder_globalOrder_increasing() {
        DocumentParserService parser = new DocumentParserService();
        ChunkingService2 chunker = new ChunkingService2();

        Map<Integer, String> pages = Map.of(1, """
                1. Alpha
                Line1. Line2. Line3.
                1.1 Alpha One
                X. Y. Z.
                """);

        List<Section> sections = parser.parseSections(pages);
        List<DocumentChunk> chunks = chunker.processSections2(sections);

        assertThat(chunks).isNotEmpty();

        int lastOrderIndex = -1;
        int lastSectionOrder = -1;
        for (DocumentChunk c : chunks) {
            assertThat(c.orderIndex()).isGreaterThan(lastOrderIndex);
            lastOrderIndex = c.orderIndex();
            assertThat(c.sectionOrder()).isGreaterThanOrEqualTo(lastSectionOrder);
            lastSectionOrder = c.sectionOrder();
        }
    }

    @Test
    void section_with_empty_content_between_headings_must_still_be_created() {
        DocumentParserService parser = new DocumentParserService();
        Map<Integer, String> pages = Map.of(1, """
                2. Overview
                2.1 Scope
                Some scope text.
                """);

        List<Section> sections = parser.parseSections(pages);

        Section parentSection = sections.stream()
                .filter(s -> s.header().contains("Overview"))
                .findFirst().orElse(null);
        assertThat(parentSection).as("Section '2. Overview' phải được tạo dù content rỗng").isNotNull();
        assertThat(parentSection.content().trim()).isEmpty();

        Section childSection = sections.stream()
                .filter(s -> s.header().contains("Scope"))
                .findFirst().orElse(null);
        assertThat(childSection).isNotNull();
        assertThat(childSection.content()).contains("Some scope text");
    }

    // ═══════════════════════════════════════════════════════════════════
    // NEW TEST: parent_section_summary chunk
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Mỗi section cha (có section con) phải tạo ra đúng 1 chunk type=parent_section_summary.
     * Điều này bảo đảm câu hỏi về section cha không bị "không tìm thấy".
     */
    @Test
    void parent_section_with_children_must_produce_parent_section_summary_chunk() {
        DocumentParserService parser = new DocumentParserService();
        ChunkingService2 chunker = new ChunkingService2();

        Map<Integer, String> pages = Map.of(1, """
                6. System Features
                6.1 Authentication
                Users must log in with username and password.
                6.2 Authorization
                Roles: Admin, User, Guest.
                6.3 Audit Log
                All actions are logged.
                """);

        List<Section> sections = parser.parseSections(pages);
        List<DocumentChunk> chunks = chunker.processSections2(sections);

        // Phải có đúng 1 chunk type=parent_section_summary cho "6. System Features"
        List<DocumentChunk> parentSummaries = chunks.stream()
                .filter(c -> "parent_section_summary".equals(c.chunkType()))
                .toList();

        assertThat(parentSummaries)
                .as("Phải có đúng 1 parent_section_summary cho section cha '6. System Features'")
                .hasSize(1);

        DocumentChunk parentSummary = parentSummaries.get(0);
        assertThat(parentSummary.sectionId()).isEqualTo("sec_6");
        assertThat(parentSummary.content())
                .as("parent_section_summary phải liệt kê các section con")
                .contains("6.1").contains("6.2").contains("6.3");
        assertThat(parentSummary.childSectionIds())
                .as("childSectionIds phải chứa sec_6.1, sec_6.2, sec_6.3")
                .contains("sec_6.1").contains("sec_6.2").contains("sec_6.3");
    }

    /**
     * Section cha rỗng (content = "") + có children → phải tạo parent_section_summary.
     * Không tạo WARN mà chỉ INFO.
     */
    @Test
    void parent_section_empty_content_but_has_children_must_still_create_summary() {
        DocumentParserService parser = new DocumentParserService();
        ChunkingService2 chunker = new ChunkingService2();

        Map<Integer, String> pages = Map.of(1, """
                3. Requirements
                3.1 Functional Requirements
                The system shall allow users to create accounts.
                3.2 Non-Functional Requirements
                Response time must be under 200ms.
                """);

        List<Section> sections = parser.parseSections(pages);

        // Kiểm tra section "3. Requirements" có content rỗng
        Section parent = sections.stream()
                .filter(s -> s.header().contains("Requirements") && !s.header().contains("Functional"))
                .findFirst().orElse(null);
        assertThat(parent).isNotNull();
        assertThat(parent.content().trim()).isEmpty();

        // Chunking phải vẫn tạo được parent_section_summary
        List<DocumentChunk> chunks = chunker.processSections2(sections);
        boolean hasParentSummary = chunks.stream()
                .anyMatch(c -> "parent_section_summary".equals(c.chunkType())
                        && c.sectionId().equals("sec_3"));
        assertThat(hasParentSummary)
                .as("parent_section_summary phải được tạo dù section cha content rỗng")
                .isTrue();
    }

    /**
     * childSectionIds phải trỏ đến các sectionId thật sự tồn tại trong chunks.
     * Đây là điều kiện để retrieval expansion hoạt động đúng.
     */
    @Test
    void child_section_ids_must_reference_existing_section_ids() {
        DocumentParserService parser = new DocumentParserService();
        ChunkingService2 chunker = new ChunkingService2();

        Map<Integer, String> pages = Map.of(1, """
                5. Design
                5.1 Architecture
                Microservices with REST APIs.
                5.2 Database
                PostgreSQL for relational data.
                5.3 API
                OpenAPI 3.0 specification.
                """);

        List<Section> sections = parser.parseSections(pages);
        List<DocumentChunk> chunks = chunker.processSections2(sections);

        // Thu thập tất cả sectionId đã index
        var allSectionIds = chunks.stream()
                .map(DocumentChunk::sectionId)
                .collect(java.util.stream.Collectors.toSet());

        // Kiểm tra childSectionIds của parent_section_summary
        chunks.stream()
                .filter(c -> "parent_section_summary".equals(c.chunkType()))
                .forEach(parent -> {
                    assertThat(parent.childSectionIds())
                            .as("parent_section_summary phải có childSectionIds không rỗng")
                            .isNotBlank();

                    String[] childIds = parent.childSectionIds().split(",");
                    for (String childId : childIds) {
                        assertThat(allSectionIds)
                                .as("childSectionId '%s' phải tồn tại trong các section đã chunk".formatted(childId))
                                .contains(childId.trim());
                    }
                });
    }

    /**
     * buildParentChildMap() phải detect đúng quan hệ cha-con dựa trên section number hierarchy.
     */
    @Test
    void build_parent_child_map_detects_correct_hierarchy() {
        ChunkingService2 chunker = new ChunkingService2();

        // sections: 1 (cha của 1.1, 1.2), 1.1 (cha của 1.1.1), 1.1.1, 1.2, 2
        List<Section> sections = List.of(
                new Section("1. System Overview", 1, 2, "Intro", 0, 1),
                new Section("1.1 Architecture", 1, 1, "Arch detail", 1, 2),
                new Section("1.1.1 Backend", 1, 1, "Backend detail", 2, 3),
                new Section("1.2 Database", 2, 2, "DB detail", 3, 2),
                new Section("2. Requirements", 3, 3, "", 4, 1)
        );

        Map<String, List<Section>> map = chunker.buildParentChildMap(sections);

        // sec_1 phải có 2 children: 1.1 và 1.2
        assertThat(map).containsKey("sec_1");
        assertThat(map.get("sec_1")).hasSize(2);
        assertThat(map.get("sec_1").stream().map(s -> chunker.extractSectionNumber(s.header())).toList())
                .containsExactlyInAnyOrder("1.1", "1.2");

        // sec_1.1 phải có 1 child: 1.1.1
        assertThat(map).containsKey("sec_1.1");
        assertThat(map.get("sec_1.1")).hasSize(1);

        // sec_2 KHÔNG phải parent (không có con)
        assertThat(map).doesNotContainKey("sec_2");
    }

    // ═══════════════════════════════════════════════════════════════════
    // NEW TEST: pseudo-table detection
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Text có cấu trúc dạng bảng (space-aligned, 3+ cột, 3+ dòng liên tiếp)
     * phải được convert thành [TABLE_START]...[TABLE_END] và tạo table chunks.
     */
    @Test
    void pseudo_table_in_plain_text_must_be_detected_and_converted() {
        ChunkingService2 chunker = new ChunkingService2();

        // Space-aligned table (3 cột, 4 dòng)
        String textWithPseudoTable = """
                Một số ghi chú đầu section.
                
                STT  Tên chức năng         Mô tả
                1    Đăng nhập             Người dùng nhập username/password
                2    Đăng ký               Người dùng tạo tài khoản mới
                3    Quản lý hồ sơ         Người dùng cập nhật thông tin
                
                Ghi chú cuối.
                """;

        String result = chunker.detectAndConvertTextTables(textWithPseudoTable);

        assertThat(result)
                .as("Pseudo-table phải được bọc trong [TABLE_START]...[TABLE_END]")
                .contains("[TABLE_START]")
                .contains("[TABLE_END]");

        // Sau khi convert, splitByTableBlocks phải tách ra được table segment
        List<Section> fakeSections = List.of(
                new Section("10. Use Cases", 1, 1, textWithPseudoTable, 0, 1));
        DocumentParserService parser = new DocumentParserService();

        List<DocumentChunk> chunks = chunker.processSections2(fakeSections);
        boolean hasTableChunk = chunks.stream()
                .anyMatch(c -> "table_summary".equals(c.chunkType())
                        || "table_row_group".equals(c.chunkType()));
        assertThat(hasTableChunk)
                .as("Pseudo-table phải tạo ra table_summary hoặc table_row_group chunk")
                .isTrue();
    }

    /**
     * Text bình thường (không có bảng) không được bị convert thành pseudo-table.
     */
    @Test
    void normal_paragraph_text_must_not_be_converted_to_pseudo_table() {
        ChunkingService2 chunker = new ChunkingService2();

        // Đoạn văn thường, không có bảng
        String normalText = """
                Hệ thống này được thiết kế để hỗ trợ người dùng quản lý tài liệu.
                Người dùng có thể tải lên, tìm kiếm và xem lại tài liệu bất kỳ lúc nào.
                Hệ thống hỗ trợ nhiều định dạng tài liệu khác nhau bao gồm PDF và DOCX.
                Tất cả tài liệu được mã hóa và lưu trữ an toàn trên máy chủ.
                """;

        String result = chunker.detectAndConvertTextTables(normalText);

        assertThat(result)
                .as("Text bình thường không được bọc TABLE markers")
                .doesNotContain("[TABLE_START]")
                .doesNotContain("[TABLE_END]");
    }

    // ═══════════════════════════════════════════════════════════════════
    // NEW TEST: multi-level hierarchy + parent summary content
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Với tài liệu có 3 cấp heading, parent_section_summary phải được tạo
     * cho mọi level có children, không chỉ level 1.
     */
    @Test
    void multi_level_hierarchy_must_create_parent_summaries_at_every_level() {
        DocumentParserService parser = new DocumentParserService();
        ChunkingService2 chunker = new ChunkingService2();

        Map<Integer, String> pages = Map.of(1, """
                2. System Design
                2.1 Backend
                2.1.1 REST API
                GET /api/users returns list of users.
                2.1.2 Database
                PostgreSQL schema with 5 tables.
                2.2 Frontend
                React SPA with TypeScript.
                """);

        List<Section> sections = parser.parseSections(pages);
        List<DocumentChunk> chunks = chunker.processSections2(sections);

        // sec_2 phải có parent_section_summary (children: 2.1, 2.2)
        assertThat(chunks.stream()
                .anyMatch(c -> "parent_section_summary".equals(c.chunkType())
                        && "sec_2".equals(c.sectionId())))
                .as("sec_2 phải có parent_section_summary")
                .isTrue();

        // sec_2.1 phải có parent_section_summary (children: 2.1.1, 2.1.2)
        assertThat(chunks.stream()
                .anyMatch(c -> "parent_section_summary".equals(c.chunkType())
                        && "sec_2.1".equals(c.sectionId())))
                .as("sec_2.1 phải có parent_section_summary")
                .isTrue();

        // sec_2.2 KHÔNG phải parent (không có children)
        assertThat(chunks.stream()
                .anyMatch(c -> "parent_section_summary".equals(c.chunkType())
                        && "sec_2.2".equals(c.sectionId())))
                .as("sec_2.2 không phải parent → không có parent_section_summary")
                .isFalse();

        // sec_2 childSectionIds phải chứa sec_2.1 và sec_2.2
        DocumentChunk sec2Summary = chunks.stream()
                .filter(c -> "parent_section_summary".equals(c.chunkType())
                        && "sec_2".equals(c.sectionId()))
                .findFirst().orElse(null);
        assertThat(sec2Summary).isNotNull();
        assertThat(sec2Summary.childSectionIds())
                .contains("sec_2.1")
                .contains("sec_2.2")
                .doesNotContain("sec_2.1.1") // chỉ DIRECT children
                .doesNotContain("sec_2.1.2");
    }

    /**
     * Sau khi tạo parent_section_summary cho section cha rỗng,
     * các section con (6.1, 6.2, 6.3) vẫn phải có chunk riêng của chúng.
     * Tránh regression: parent summary không làm mất chunks của children.
     */
    @Test
    void child_sections_must_still_have_their_own_chunks_after_parent_summary_created() {
        DocumentParserService parser = new DocumentParserService();
        ChunkingService2 chunker = new ChunkingService2();

        Map<Integer, String> pages = Map.of(1, """
                6. Features
                6.1 Login
                User enters credentials and clicks Login.
                6.2 Dashboard
                Shows summary of recent activity.
                """);

        List<Section> sections = parser.parseSections(pages);
        List<DocumentChunk> chunks = chunker.processSections2(sections);

        // Kiểm tra sec_6.1 có text chunk riêng
        assertThat(chunks.stream()
                .anyMatch(c -> "text".equals(c.chunkType()) && "sec_6.1".equals(c.sectionId())))
                .as("sec_6.1 phải có text chunk riêng")
                .isTrue();

        // Kiểm tra sec_6.2 có text chunk riêng
        assertThat(chunks.stream()
                .anyMatch(c -> "text".equals(c.chunkType()) && "sec_6.2".equals(c.sectionId())))
                .as("sec_6.2 phải có text chunk riêng")
                .isTrue();

        // Kiểm tra sec_6 có parent_section_summary
        assertThat(chunks.stream()
                .anyMatch(c -> "parent_section_summary".equals(c.chunkType())
                        && "sec_6".equals(c.sectionId())))
                .as("sec_6 phải có parent_section_summary")
                .isTrue();
    }

    /**
     * Validation: buildChildSectionIdsStr phải tạo đúng format "sec_X.1,sec_X.2,sec_X.3".
     */
    @Test
    void build_child_section_ids_str_format_is_correct() {
        ChunkingService2 chunker = new ChunkingService2();

        List<Section> children = List.of(
                new Section("6.1 Login", 1, 1, "content", 1, 2),
                new Section("6.2 Dashboard", 1, 1, "content", 2, 2),
                new Section("6.3 Settings", 1, 1, "content", 3, 2)
        );

        String result = chunker.buildChildSectionIdsStr(children);

        assertThat(result).isEqualTo("sec_6.1,sec_6.2,sec_6.3");
    }

    /**
     * Regression 23B: bảng thực thể gộp một khối (sau merge cross-page) phải index đủ rows
     * cho cả phần "trang 13" lẫn "trang 14" (LichSuPhucKhao … BienBan).
     */
    @Test
    void merged_cross_page_entity_table_indexes_all_entity_rows() {
        DocumentParserService parser = new DocumentParserService();
        ChunkingService2 chunker = new ChunkingService2();

        String mergedTable = """
                [TABLE_START]
                | Thực thể | Thuộc tính |
                | --- | --- |
                | NguoiDung | id, email |
                | ThongBao | id, noiDung |
                | SinhVien | maSV, hoTen |
                | GiangVien | maGV, hoTen |
                | MonHoc | maMon, tenMon |
                | KetQuaHocTap | diem |
                | PhongKhaoThi | maPhong |
                | YeuCauPhucKhao | maYC, trangThai |
                | LichSuPhucKhao | maLS, ngay |
                | Khoa | maKhoa, tenKhoa |
                | TuiBaiThi | maTui |
                | BienBan | maBB, noiDung |
                [TABLE_END]
                """;

        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(13, "3. Các thực thể và thuộc tính\n" + mergedTable);
        pages.put(14, "");

        List<Section> sections = parser.parseSections(pages);
        List<DocumentChunk> chunks = chunker.processSections2(sections);

        String allTableText = chunks.stream()
                .filter(c -> c.chunkType() != null && c.chunkType().startsWith("table"))
                .map(DocumentChunk::content)
                .reduce("", String::concat);

        List<String> entities = List.of(
                "NguoiDung", "ThongBao", "SinhVien", "GiangVien", "MonHoc", "KetQuaHocTap",
                "PhongKhaoThi", "YeuCauPhucKhao", "LichSuPhucKhao", "Khoa", "TuiBaiThi", "BienBan");
        for (String entity : entities) {
            assertThat(allTableText)
                    .as("Merged table chunks must contain entity %s", entity)
                    .contains(entity);
        }

        long tableSummaryCount = chunks.stream()
                .filter(c -> "table_summary".equals(c.chunkType()))
                .count();
        assertThat(tableSummaryCount)
                .as("Single merged table → one table_summary per section")
                .isEqualTo(1);
    }
}
