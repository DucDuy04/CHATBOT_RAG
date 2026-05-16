package KLTN.RAG_CHATBOT_BE;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSectionRepository;
import KLTN.RAG_CHATBOT_BE.service.QueryAnalyzerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link QueryAnalyzerService#analyze(String, java.util.UUID)} — no DB.
 */
@ExtendWith(MockitoExtension.class)
class QueryAnalyzerServiceTest {

    @Mock
    private DocumentSectionRepository documentSectionRepository;

    private QueryAnalyzerService queryAnalyzerService;

    @BeforeEach
    void setUp() {
        queryAnalyzerService = new QueryAnalyzerService(documentSectionRepository);
    }

    @Test
    void golden_tableLookup_regression() {
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Giá gói Basic là bao nhiêu VNĐ theo bảng?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Theo bảng gói dịch vụ, Basic có giá bao nhiêu?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Trong bảng, dòng Basic có giá là bao nhiêu?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Gói Pro có bao nhiêu lượt hỏi AI mỗi tháng theo bảng giá?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Gói Business được hỗ trợ theo kênh nào trong bảng?"));
    }

    @Test
    void golden_countQuery_regression() {
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Có bao nhiêu gói dịch vụ?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Trong bảng có bao nhiêu gói?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Có bao nhiêu chính sách hỗ trợ?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Đếm số gói trong bảng."));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Số lượng chính sách là bao nhiêu?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Bảng gói dịch vụ có tất cả bao nhiêu hàng gói (số gói)?"));
    }

    @Test
    void generalization_tableLookup_noGoldenNamesRequired() {
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Giá sản phẩm Laptop X là bao nhiêu trong bảng?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Sản phẩm Máy in A có tồn kho bao nhiêu theo bảng?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Mã SP001 có trạng thái gì trong bảng?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Nhân viên Nguyễn Văn A thuộc phòng ban nào trong bảng?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Dịch vụ Premium Plus được hỗ trợ theo kênh nào?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Dòng ABC có giá trị cột Hỗ trợ là gì?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Theo bảng, SKU-123 có giá là bao nhiêu?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Gói Enterprise có bao nhiêu lượt sử dụng mỗi tháng?"));
    }

    @Test
    void generalization_countQueries() {
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Có bao nhiêu sản phẩm trong bảng?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Bảng này có bao nhiêu dòng?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Có bao nhiêu nhân viên trong danh sách?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Đếm số dịch vụ hiện có."));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Số lượng dòng dữ liệu là bao nhiêu?"));
    }

    @Test
    void negative_noFalseTableLookupFromProSubstring() {
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Process này có bao nhiêu bước?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Profile người dùng có bao nhiêu trường?"));
    }

    @Test
    void edge_countEnterprisePackages_vs_rowPrice() {
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Có bao nhiêu gói Enterprise?"));
        assertEquals(QueryAnalyzerService.QueryType.COUNT_QUERY, q(
                "Có bao nhiêu dòng trong bảng?"));
        assertEquals(QueryAnalyzerService.QueryType.TABLE_LOOKUP, q(
                "Dòng Enterprise có giá bao nhiêu?"));
    }

    private QueryAnalyzerService.QueryType q(String question) {
        return queryAnalyzerService.analyze(question, null);
    }
}
