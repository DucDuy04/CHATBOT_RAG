package KLTN.RAG_CHATBOT_BE.ingest.normalize;

import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableCell;
import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableModel;
import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableRow;
import java.util.ArrayList;
import java.util.List;

/** Synthetic {@link RawTableModel} builders for normalized-table regression tests. */
final class TestRawTableFixtures {

    private TestRawTableFixtures() {}

    static RawTableCell docxCell(int row, int col, String text, int gridSpan) {
        return new RawTableCell(
                text,
                row,
                col,
                1,
                col,
                row,
                gridSpan,
                1.0,
                col + gridSpan,
                row + 1.0,
                0,
                RawTableModel.ExtractorType.DOCX,
                "docx_r" + row + "_c" + col);
    }

    static RawTableCell spreadsheetCell(int row, int col, String text, double x, double xEnd) {
        return new RawTableCell(
                text,
                row,
                col,
                2,
                x,
                row,
                xEnd - x,
                1.0,
                xEnd,
                row + 1.0,
                0,
                RawTableModel.ExtractorType.SPREADSHEET,
                "pdf_r" + row + "_c" + col);
    }

    /**
     * DOCX schedule-like table: header row + merged group-header + data row (24D2 regression).
     */
    static RawTableModel docxScheduleWithGroupHeaderRow() {
        String[] headers = {
                "STT", "Mã học phần", "Tên lớp học phần", "Nhóm", "Số TC", "Số SV",
                "Giảng viên", "Ngày bắt đầu", "Tiết học", "Phòng", "Ghi chú"
        };
        List<RawTableRow> rows = new ArrayList<>();
        List<RawTableCell> headerCells = new ArrayList<>();
        for (int c = 0; c < headers.length; c++) {
            headerCells.add(docxCell(0, c, headers[c], 1));
        }
        rows.add(new RawTableRow(0, headerCells));

        rows.add(new RawTableRow(1, List.of(
                docxCell(1, 0, "Ngành: Công nghệ thông tin - Khóa K46", 8)
        )));

        String[] values = {
                "1", "LUA1012", "Pháp luật Việt Nam đại cương - Nhóm 1", "Nhóm 1",
                "2", "0", "Nguyễn Thị Vân Anh", "08/09/2025", "1 - 2", "E301", ""
        };
        List<RawTableCell> dataCells = new ArrayList<>();
        for (int c = 0; c < values.length; c++) {
            if (!values[c].isBlank()) {
                dataCells.add(docxCell(2, c, values[c], 1));
            }
        }
        rows.add(new RawTableRow(2, dataCells));

        return rawTable("docx_schedule", rows);
    }

    /** Curriculum-style DOCX row: Khóa ngành / Học kỳ columns map 1:1. */
    static RawTableModel docxCurriculumRow() {
        List<RawTableRow> rows = new ArrayList<>();
        rows.add(new RawTableRow(0, List.of(
                docxCell(0, 0, "Mã HP", 1),
                docxCell(0, 1, "Tên học phần", 1),
                docxCell(0, 2, "Khóa ngành", 1),
                docxCell(0, 3, "Học kỳ", 1)
        )));
        rows.add(new RawTableRow(1, List.of(
                docxCell(1, 0, "TIN1093", 1),
                docxCell(1, 1, "Tin học đại cương", 1),
                docxCell(1, 2, "Công nghệ thông tin", 1),
                docxCell(1, 3, "Học kỳ 1", 1)
        )));
        return rawTable("docx_curriculum", rows);
    }

    /** Minimal DOCX table: header A|B|C + one data row. */
    static RawTableModel docxSimpleAbcTable() {
        List<RawTableRow> rows = new ArrayList<>();
        rows.add(new RawTableRow(0, List.of(
                docxCell(0, 0, "A", 1),
                docxCell(0, 1, "B", 1),
                docxCell(0, 2, "C", 1)
        )));
        rows.add(new RawTableRow(1, List.of(
                docxCell(1, 0, "v1", 1),
                docxCell(1, 1, "v2", 1),
                docxCell(1, 2, "v3", 1)
        )));
        return rawTable("docx_simple_abc", rows);
    }

    /**
     * Broad parent header spanning all columns must not become column keys for the data row.
     */
    static RawTableModel docxBroadParentHeaderTable() {
        List<RawTableRow> rows = new ArrayList<>();
        rows.add(new RawTableRow(0, List.of(
                docxCell(0, 0, "Parent spanning A+B+C", 3)
        )));
        rows.add(new RawTableRow(1, List.of(
                docxCell(1, 0, "A", 1),
                docxCell(1, 1, "B", 1),
                docxCell(1, 2, "C", 1)
        )));
        rows.add(new RawTableRow(2, List.of(
                docxCell(2, 0, "v1", 1),
                docxCell(2, 1, "v2", 1),
                docxCell(2, 2, "v3", 1)
        )));
        return rawTable("docx_broad_parent", rows);
    }

    /** PDF-style row: x-overlap path must still assign columns (not DOCX direct index). */
    static RawTableModel pdfCoordinateTable() {
        List<RawTableRow> rows = new ArrayList<>();
        rows.add(new RawTableRow(0, List.of(
                spreadsheetCell(0, 0, "STT", 0, 1),
                spreadsheetCell(0, 1, "Mã HP", 1, 2),
                spreadsheetCell(0, 2, "Tên HP", 2, 3)
        )));
        rows.add(new RawTableRow(1, List.of(
                spreadsheetCell(1, 0, "1", 0, 1),
                spreadsheetCell(1, 1, "ABC101", 1, 2),
                spreadsheetCell(1, 2, "Môn mẫu", 2, 3)
        )));
        return new RawTableModel(
                "pdf_tbl",
                null,
                2,
                0,
                RawTableModel.ExtractorType.SPREADSHEET,
                0, 0, 3, 2,
                rows,
                null,
                null,
                null);
    }

    private static RawTableModel rawTable(String id, List<RawTableRow> rows) {
        return new RawTableModel(
                id,
                null,
                1,
                0,
                RawTableModel.ExtractorType.DOCX,
                0, 0, 11, 3,
                rows,
                null,
                null,
                null);
    }
}
