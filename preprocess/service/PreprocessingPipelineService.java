// package KLTN.RAG_CHATBOT_BE.preprocess.service;

// import KLTN.RAG_CHATBOT_BE.preprocess.cleaner.DocumentCleaner;
// import KLTN.RAG_CHATBOT_BE.preprocess.model.CleaningContext;
// import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedDocument;
// import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedPage;
// import KLTN.RAG_CHATBOT_BE.preprocess.parser.ParsedDocumentParser;
// import org.springframework.stereotype.Service;
// import org.springframework.web.multipart.MultipartFile;

// import java.io.IOException;
// import java.util.HashMap;
// import java.util.Map;

// @Service
// public class PreprocessingPipelineService {

//     private final ParsedDocumentParser parser;
//     private final DocumentCleaner cleaner;

//     public PreprocessingPipelineService(ParsedDocumentParser parser, DocumentCleaner cleaner) {
//         this.parser = parser;
//         this.cleaner = cleaner;
//     }

//     public ParsedDocument preprocess(MultipartFile file) throws IOException {
//         ParsedDocument parsed = parser.parse(file);

//         Map<String, String> contextMetadata = new HashMap<>();
//         contextMetadata.put("source", "upload");
//         contextMetadata.put("cleaner", cleaner.getClass().getSimpleName());

//         CleaningContext context = new CleaningContext(
//                 parsed.sourceName(),
//                 parsed.sourceType(),
//                 parsed.pages() == null ? 0 : parsed.pages().size(),
//                 contextMetadata
//         );

//         return cleaner.clean(parsed, context);
//     }

//     // Helper để tích hợp dần với ChunkingService hiện tại (đang nhận String).
//     public String toFlatText(ParsedDocument document) {
//         if (document.pages() == null || document.pages().isEmpty()) {
//             return "";
//         }

//         StringBuilder sb = new StringBuilder();
//         for (ParsedPage page : document.pages()) {
//             String text = page.cleanedText() != null ? page.cleanedText() : page.rawText();
//             if (text != null && !text.isBlank()) {
//                 sb.append(text).append("\n\n");
//             }
//         }
//         return sb.toString().trim();
//     }
// }
