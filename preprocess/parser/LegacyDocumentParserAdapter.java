// package KLTN.RAG_CHATBOT_BE.preprocess.parser;

// import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedDocument;
// import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;
// import KLTN.RAG_CHATBOT_BE.service.DocumentParserService;
// import org.springframework.stereotype.Service;
// import org.springframework.web.multipart.MultipartFile;

// import java.io.IOException;
// import java.util.List;
// import java.util.Map;

// @Service
// public class LegacyDocumentParserAdapter implements ParsedDocumentParser {

//     private final DocumentParserService legacyParser;

//     public LegacyDocumentParserAdapter(DocumentParserService legacyParser) {
//         this.legacyParser = legacyParser;
//     }

//     @Override
//     public ParsedDocument parse(MultipartFile file) throws IOException {
//         // String rawText = legacyParser.parse(file);

//         // ParsedPage page = ParsedPage.builder()
//         //         .pageNumber(1)
//         //         .rawText(rawText)
//         //         .cleanedText(rawText)
//         //         .tableBlocks(List.of())
//         //         .metadata(Map.of("parser", "legacy-adapter"))
//         //         .build();

//         // return ParsedDocument.builder()
//         //         .sourceName(file.getOriginalFilename())
//         //         .sourceType(resolveType(file.getOriginalFilename()))
//         //         .sourceSize(file.getSize())
//         //         .pages(List.of(page))
//         //         .metadata(Map.of("pipeline", "preprocess-v1"))
//         //         .build();
//         return null; // Tạm thời chưa implement, vì nếu dùng adapter này thì sẽ bỏ qua phần parsing cũ và trả về null. Bạn có thể tùy chỉnh logic này để phù hợp với cách bạn muốn tích hợp giữa legacy parser và new parser nhé.
//     }

//     private String resolveType(String fileName) {
//         if (fileName == null) {
//             return "UNKNOWN";
//         }
//         String lower = fileName.toLowerCase();
//         if (lower.endsWith(".pdf")) {
//             return "PDF";
//         }
//         if (lower.endsWith(".txt")) {
//             return "TXT";
//         }
//         return "UNKNOWN";
//     }
// }
