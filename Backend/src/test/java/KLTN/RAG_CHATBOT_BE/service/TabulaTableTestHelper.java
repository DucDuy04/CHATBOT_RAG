package KLTN.RAG_CHATBOT_BE.service;

import technology.tabula.Table;
import technology.tabula.TextChunk;
import technology.tabula.TextElement;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * Builds minimal Tabula {@link Table} instances for parser merge unit tests.
 */
final class TabulaTableTestHelper {

    private TabulaTableTestHelper() {}

    static TextChunk textChunk(String text) {
        TextChunk chunk = new TextChunk(0, 0, 10, 10);
        if (text != null && !text.isBlank()) {
            chunk.add(new TextElement(0, 0, 10, 10, null, 10, text, 4));
        }
        return chunk;
    }

    static Table table(String[][] rows) {
        Table t = new Table(new SpreadsheetExtractionAlgorithm());
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                String v = rows[r][c];
                t.add(v == null || v.isEmpty() ? TextChunk.EMPTY : textChunk(v), r, c);
            }
        }
        return t;
    }
}
