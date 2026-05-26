package KLTN.RAG_CHATBOT_BE.diagnostic;

import KLTN.RAG_CHATBOT_BE.service.NormalizedTableService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class IngestTrace23LDiagnosticTest {

    private static final Path PDF =
            Path.of("..", "docs", "eval", "manual", "SoTayHocVu-HocKy1-NamHoc20252026.pdf");
    private static final Path OUT =
            Path.of("target", "INGEST_FILE_DETECTION_TRACE_23L_EVIDENCE.md");

    @Test
    void capturePage20RawAndTabulaEvidence() throws Exception {
        StringBuilder out = new StringBuilder();
        byte[] bytes = Files.readAllBytes(PDF);
        try (PDDocument document = Loader.loadPDF(bytes)) {
            List<Integer> matchingPages = new ArrayList<>();
            PDFTextStripper scanner = new PDFTextStripper();
            scanner.setSortByPosition(true);
            scanner.setWordSeparator(" ");
            scanner.setLineSeparator("\n");
            for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
                scanner.setStartPage(pageNo);
                scanner.setEndPage(pageNo);
                String page = scanner.getText(document);
                if (page.contains("KTR3185") || page.contains("Đồ án kiến trúc công trình tổ hợp đa chức năng")) {
                    matchingPages.add(pageNo);
                }
            }
            out.append("# 23L Diagnostic Evidence\n\n");
            out.append("## PDF Pages Matching KTR3185/Title\n\n");
            out.append(matchingPages).append("\n\n");

            int diagnosticPage = matchingPages.isEmpty() ? 20 : matchingPages.get(0);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setWordSeparator(" ");
            stripper.setLineSeparator("\n");
            stripper.setStartPage(diagnosticPage);
            stripper.setEndPage(diagnosticPage);
            String pageText = stripper.getText(document);

            out.append("## Raw PDFBox Page ").append(diagnosticPage).append(" Snippet\n\n```text\n");
            out.append(snippetAround(pageText, "KTR3185", 3500));
            out.append("\n```\n\n");

            ObjectExtractor extractor = new ObjectExtractor(document);
            Page page = extractor.extract(diagnosticPage);
            SpreadsheetExtractionAlgorithm spreadsheet = new SpreadsheetExtractionAlgorithm();
            BasicExtractionAlgorithm basic = new BasicExtractionAlgorithm();
            List<Table> spreadsheetTables = spreadsheet.extract(page);
            List<Table> basicTables = basic.extract(page);
            out.append("## Tabula Page 20 Table Counts\n\n");
            out.append("- SpreadsheetExtractionAlgorithm tables: ").append(spreadsheetTables.size()).append("\n");
            out.append("- BasicExtractionAlgorithm tables: ").append(basicTables.size()).append("\n\n");
            appendTables(out, "Spreadsheet", spreadsheetTables);
            appendTables(out, "Basic", basicTables);
        }
        Files.writeString(OUT, out.toString());
    }

    private static void appendTables(StringBuilder out, String label, List<Table> tables) {
        NormalizedTableService normalizer = new NormalizedTableService();
        for (int t = 0; t < tables.size(); t++) {
            Table table = tables.get(t);
            List<List<RectangularTextContainer>> rows = table.getRows();
            int maxCols = rows.stream().mapToInt(List::size).max().orElse(0);
            String markdown = toMarkdown(rows);
            boolean contains = markdown.contains("KTR3185")
                    || markdown.contains("Đồ án kiến trúc công trình tổ hợp đa chức năng");
            out.append("## ").append(label).append(" Table ").append(t).append("\n\n");
            out.append("- rows: ").append(rows.size()).append("\n");
            out.append("- max columns: ").append(maxCols).append("\n");
            out.append("- contains KTR3185/title: ").append(contains).append("\n\n");
            if (!contains) {
                continue;
            }
            out.append("### Physical Rows\n\n```text\n");
            for (int r = 0; r < rows.size(); r++) {
                out.append("row ").append(r).append("\n");
                List<RectangularTextContainer> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    RectangularTextContainer cell = row.get(c);
                    String text = cell.getText() == null ? "" : cell.getText().trim().replaceAll("\\s+", " ");
                    out.append("  col ").append(c)
                            .append(" x=").append(fmt(cell.getLeft()))
                            .append(" y=").append(fmt(cell.getTop()))
                            .append(" w=").append(fmt(cell.getWidth()))
                            .append(" h=").append(fmt(cell.getHeight()))
                            .append(" text='").append(text).append("'\n");
                }
            }
            out.append("```\n\n");
            out.append("### Markdown Approximation\n\n```markdown\n");
            out.append(markdown);
            out.append("\n```\n\n");
            NormalizedTableService.NormalizationResult result = normalizer.normalize(
                    new NormalizedTableService.NormalizationRequest(
                            markdown, "2. Thi kết thúc học phần", null, 20, 20, t, null));
            out.append("### Normalizer Output\n\n");
            out.append("- success: ").append(result.success()).append("\n");
            out.append("- failureReason: ").append(result.failureReason()).append("\n");
            out.append("- tableName: ").append(result.tableName()).append("\n");
            out.append("- rows: ").append(result.rows().size()).append("\n\n");
            out.append("```text\n");
            result.rows().stream()
                    .filter(row -> row.canonicalText().contains("KTR3185"))
                    .forEach(row -> out.append(row.canonicalText())
                            .append("\nCELLS: ").append(row.cellsJson()).append("\n\n"));
            out.append("```\n\n");
        }
    }

    private static String toMarkdown(List<List<RectangularTextContainer>> rows) {
        int maxCols = rows.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> textRows = new ArrayList<>();
        for (List<RectangularTextContainer> row : rows) {
            List<String> texts = new ArrayList<>();
            for (RectangularTextContainer cell : row) {
                texts.add(cell.getText() == null ? "" : cell.getText().trim().replaceAll("\\s+", " "));
            }
            textRows.add(texts);
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < textRows.size(); r++) {
            sb.append("| ");
            List<String> row = textRows.get(r);
            for (int c = 0; c < maxCols; c++) {
                sb.append(c < row.size() ? row.get(c) : "").append(" | ");
            }
            sb.append("\n");
            if (r == 0) {
                sb.append("| ");
                for (int c = 0; c < maxCols; c++) {
                    sb.append("--- | ");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String snippetAround(String text, String needle, int radius) {
        int idx = text.indexOf(needle);
        if (idx < 0) {
            return text.substring(0, Math.min(text.length(), radius));
        }
        int start = Math.max(0, idx - radius);
        int end = Math.min(text.length(), idx + radius);
        return text.substring(start, end);
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
