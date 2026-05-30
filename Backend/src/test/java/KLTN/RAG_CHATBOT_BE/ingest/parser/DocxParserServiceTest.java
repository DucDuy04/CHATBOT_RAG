package KLTN.RAG_CHATBOT_BE.ingest.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocxParserServiceTest {

    private DocumentParserService parser;

    @BeforeEach
    void setUp() {
        parser = new DocumentParserService();
    }

    @Test
    void simpleTable_preservesLogicalColumnIndices() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 3);
            setCellText(table.getRow(0).getCell(0), "A");
            setCellText(table.getRow(0).getCell(1), "B");
            setCellText(table.getRow(0).getCell(2), "C");
            setCellText(table.getRow(1).getCell(0), "1");
            setCellText(table.getRow(1).getCell(1), "2");
            setCellText(table.getRow(1).getCell(2), "3");

            RawTableModel model = parser.convertDocxTableToRawTableModel(table, 0, "simple.docx");
            assertNotNull(model);
            assertEquals(RawTableModel.ExtractorType.DOCX, model.extractorType());

            RawTableRow dataRow = model.rows().get(1);
            assertEquals(3, dataRow.cells().size());
            assertEquals(0, dataRow.cells().get(0).physicalColIndex());
            assertEquals(1, dataRow.cells().get(1).physicalColIndex());
            assertEquals(2, dataRow.cells().get(2).physicalColIndex());
        }
    }

    @Test
    void gridSpan_advancesLogicalColumn_withoutShiftingFollowingCells() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 3);
            setCellText(table.getRow(0).getCell(0), "H0");
            setCellText(table.getRow(0).getCell(1), "H1");
            setCellText(table.getRow(0).getCell(2), "H2");

            setCellText(table.getRow(1).getCell(0), "Merged");
            setGridSpan(table.getRow(1).getCell(0), 2);
            setCellText(table.getRow(1).getCell(1), "Tail");

            RawTableModel model = parser.convertDocxTableToRawTableModel(table, 0, "span.docx");
            RawTableRow dataRow = model.rows().get(1);
            assertTrue(dataRow.cells().size() >= 2);
            RawTableCell merged = dataRow.cells().stream()
                    .filter(c -> "Merged".equals(c.text()))
                    .findFirst()
                    .orElseThrow();
            RawTableCell tail = dataRow.cells().stream()
                    .filter(c -> "Tail".equals(c.text()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, merged.physicalColIndex());
            assertEquals(2, tail.physicalColIndex());
            assertEquals(2, merged.width(), 0.01);
        }
    }

    @Test
    void blankCells_doNotCreateCells_butLogicalColumnsStayAligned() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 4);
            setCellText(table.getRow(0).getCell(0), "C0");
            setCellText(table.getRow(0).getCell(1), "C1");
            setCellText(table.getRow(0).getCell(2), "C2");
            setCellText(table.getRow(0).getCell(3), "C3");

            setCellText(table.getRow(1).getCell(0), "v0");
            setCellText(table.getRow(1).getCell(1), "");
            setCellText(table.getRow(1).getCell(2), "v2");
            setCellText(table.getRow(1).getCell(3), "v3");

            RawTableModel model = parser.convertDocxTableToRawTableModel(table, 0, "blank.docx");
            RawTableRow dataRow = model.rows().get(1);
            assertEquals(0, colIndex(dataRow, "v0"));
            assertEquals(2, colIndex(dataRow, "v2"));
            assertEquals(3, colIndex(dataRow, "v3"));
            assertTrue(dataRow.cells().stream().anyMatch(c -> c.physicalColIndex() == 1));
        }
    }

    @Test
    void elevenColumnRow_preservesElevenLogicalSlots() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 11);
            XWPFTableRow header = table.getRow(0);
            XWPFTableRow data = table.getRow(1);
            for (int c = 0; c < 11; c++) {
                setCellText(header.getCell(c), "H" + c);
                setCellText(data.getCell(c), "V" + c);
            }

            RawTableModel model = parser.convertDocxTableToRawTableModel(table, 0, "eleven.docx");
            RawTableRow dataRow = model.rows().get(1);
            assertEquals(11, dataRow.cells().size());
            assertEquals(10, dataRow.cells().get(10).physicalColIndex());
            assertEquals("V10", dataRow.cells().get(10).text());
        }
    }

    @Test
    @EnabledIf("stableDocxFixtureExists")
    void stableDocxFixture_whenPresent_parsesScheduleTableWithDocxExtractor() throws Exception {
        Path fixture = stableDocxFixturePath();
        try (var is = Files.newInputStream(fixture);
             XWPFDocument doc = new XWPFDocument(is)) {
            assertFalse(doc.getTables().isEmpty());
            RawTableModel model = parser.convertDocxTableToRawTableModel(doc.getTables().get(0), 0, fixture.getFileName().toString());
            assertNotNull(model);
            assertEquals(RawTableModel.ExtractorType.DOCX, model.extractorType());
            assertTrue(model.rows().size() >= 2);
            List<RawTableCell> anyData = model.rows().stream()
                    .flatMap(r -> r.cells().stream())
                    .filter(c -> c.text() != null && !c.text().isBlank())
                    .toList();
            assertFalse(anyData.isEmpty());
            assertTrue(anyData.stream().allMatch(c -> c.extractorType() == RawTableModel.ExtractorType.DOCX));
        }
    }

    static boolean stableDocxFixtureExists() {
        return Files.isRegularFile(stableDocxFixturePath());
    }

    private static Path stableDocxFixturePath() {
        return Path.of("..", "docs", "eval", "manual",
                "SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx").normalize();
    }

    private static void setCellText(XWPFTableCell cell, String text) {
        cell.setText(text);
    }

    private static int colIndex(RawTableRow row, String text) {
        return row.cells().stream()
                .filter(c -> text.equals(c.text()))
                .map(RawTableCell::physicalColIndex)
                .findFirst()
                .orElseThrow();
    }

    private static void setGridSpan(XWPFTableCell cell, int span) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTDecimalNumber gridSpan = tcPr.isSetGridSpan() ? tcPr.getGridSpan() : tcPr.addNewGridSpan();
        gridSpan.setVal(BigInteger.valueOf(span));
    }
}
