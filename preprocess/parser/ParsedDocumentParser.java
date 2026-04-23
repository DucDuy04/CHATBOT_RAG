package KLTN.RAG_CHATBOT_BE.preprocess.parser;

import KLTN.RAG_CHATBOT_BE.preprocess.model.ParsedDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ParsedDocumentParser {

    ParsedDocument parse(MultipartFile file) throws IOException;
}
