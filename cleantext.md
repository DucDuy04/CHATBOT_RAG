Các file đã tạo/cập nhật và chức năng

ParsedPage.java
Mô hình dữ liệu cấp trang: giữ raw text, cleaned text, table blocks, metadata, pageNumber.

ParsedDocument.java
Mô hình dữ liệu cấp tài liệu: gom source info + danh sách ParsedPage + metadata tổng.

CleaningContext.java
Context runtime cho cleaner: tên nguồn, loại file, tổng số trang, metadata context.

DocumentCleaner.java
Interface chính cho bước làm sạch tài liệu, giúp tách implementation theo SOLID.

PageTextFilter.java
Interface filter cấp trang, có thứ tự thực thi qua getOrder().

CleaningProperties.java
Bind cấu hình từ YAML: bật/tắt filter, regex noise, separator nối dòng.

DocumentCleanerPipeline.java
Orchestrator dạng chain/pipeline: sắp thứ tự filter, chạy tuần tự cho từng trang, hợp nhất metadata.

NoisePatternFilter.java
Loại bỏ noise theo regex cấu hình (page number, watermark kiểu draft/confidential...).

LineUnbreakFilter.java
Nối các dòng bị gãy theo heuristic ngữ cảnh (tránh nối sau dấu kết thúc câu, tránh heading/list số).

WhitespaceNormalizationFilter.java
Chuẩn hóa whitespace/unicode spaces, xuống dòng, khoảng trắng dư.

ParsedDocumentParser.java
Contract parser mới trả về ParsedDocument (không còn chỉ String).

LegacyDocumentParserAdapter.java
Adapter dùng lại parser cũ hiện tại để chuyển sang mô hình ParsedDocument, đảm bảo tương thích ngược.

PreprocessingPipelineService.java
Ví dụ tích hợp Spring thực tế: Parser -> Cleaner, và helper toFlatText để nối vào ChunkingService hiện có.

ChunkCandidate.java
Domain chunk thế hệ mới: content + page/section + metadata.

ChunkingStrategy.java
Abstraction cho strategy chunking (dễ thay thuật toán theo loại tài liệu).

SemanticAwareChunkingStrategy.java
Strategy khởi đầu cho Phase 3: tách block ngữ nghĩa theo đoạn, chia quá dài theo MAX_CHARS, gắn section/page metadata.

LineUnbreakFilterTest.java
Unit test chứng minh filter nối dòng hoạt động đúng với văn bản gãy dòng.

application.yml
Bổ sung config mặc định cho cleaner pipeline: enable flags + noise regex + line join separator.