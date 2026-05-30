package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WidgetServiceSoftDeleteChatbotTest {

    @Mock private WidgetConfigRepository widgetConfigRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentService documentService;
    @Mock private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private WidgetService widgetService;

    @Test
    void softDeleteChatbot_callsSoftDeleteDocumentForEachActiveDocument() {
        UUID chatbotId = UUID.randomUUID();
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        WidgetConfig widget = WidgetConfig.builder().id(chatbotId).name("bot").isActive(true).build();
        Document d1 = Document.builder().id(doc1).build();
        Document d2 = Document.builder().id(doc2).build();

        when(widgetConfigRepository.findById(chatbotId)).thenReturn(Optional.of(widget));
        when(documentRepository.findByWidgetConfigId(chatbotId)).thenReturn(List.of(d1, d2));

        assertTrue(widgetService.softDeleteChatbot(chatbotId));

        verify(documentService).softDeleteDocument(doc1);
        verify(documentService).softDeleteDocument(doc2);
        verify(documentService, times(2)).softDeleteDocument(any(UUID.class));
        assertNotNull(widget.getDeletedAt());
        assertFalse(widget.isActive());
        verify(widgetConfigRepository).save(widget);
    }

    @Test
    void softDeleteChatbot_withNoDocuments_stillSoftDeletesChatbot() {
        UUID chatbotId = UUID.randomUUID();
        WidgetConfig widget = WidgetConfig.builder().id(chatbotId).name("empty-bot").isActive(true).build();

        when(widgetConfigRepository.findById(chatbotId)).thenReturn(Optional.of(widget));
        when(documentRepository.findByWidgetConfigId(chatbotId)).thenReturn(List.of());

        assertTrue(widgetService.softDeleteChatbot(chatbotId));

        verify(documentService, never()).softDeleteDocument(any());
        assertNotNull(widget.getDeletedAt());
        verify(widgetConfigRepository).save(widget);
    }

    @Test
    void softDeleteChatbot_skipsDocumentsNotReturnedByRepository() {
        UUID chatbotId = UUID.randomUUID();
        UUID activeDocId = UUID.randomUUID();
        WidgetConfig widget = WidgetConfig.builder().id(chatbotId).name("bot").isActive(true).build();
        Document activeDoc = Document.builder().id(activeDocId).build();

        when(widgetConfigRepository.findById(chatbotId)).thenReturn(Optional.of(widget));
        when(documentRepository.findByWidgetConfigId(chatbotId)).thenReturn(List.of(activeDoc));

        assertTrue(widgetService.softDeleteChatbot(chatbotId));

        verify(documentService).softDeleteDocument(activeDocId);
        verify(documentService, times(1)).softDeleteDocument(any(UUID.class));
    }

    @Test
    void softDeleteChatbot_whenDocumentDeleteFails_doesNotSoftDeleteChatbot() {
        UUID chatbotId = UUID.randomUUID();
        UUID doc1 = UUID.randomUUID();
        WidgetConfig widget = WidgetConfig.builder().id(chatbotId).name("bot").isActive(true).build();
        Document d1 = Document.builder().id(doc1).build();

        when(widgetConfigRepository.findById(chatbotId)).thenReturn(Optional.of(widget));
        when(documentRepository.findByWidgetConfigId(chatbotId)).thenReturn(List.of(d1));
        doThrow(new IllegalStateException("Qdrant purge failed"))
                .when(documentService).softDeleteDocument(doc1);

        assertThrows(IllegalStateException.class, () -> widgetService.softDeleteChatbot(chatbotId));

        assertNull(widget.getDeletedAt());
        verify(widgetConfigRepository, never()).save(widget);
    }

    @Test
    void softDeleteChatbot_whenChatbotNotFound_returnsFalse() {
        UUID chatbotId = UUID.randomUUID();
        when(widgetConfigRepository.findById(chatbotId)).thenReturn(Optional.empty());

        assertFalse(widgetService.softDeleteChatbot(chatbotId));

        verify(documentService, never()).softDeleteDocument(any());
        verify(widgetConfigRepository, never()).save(any());
    }

    @Test
    void softDeleteChatbot_savesChatbotAfterAllDocumentDeletes() {
        UUID chatbotId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        WidgetConfig widget = WidgetConfig.builder().id(chatbotId).name("bot").isActive(true).build();
        Document doc = Document.builder().id(docId).build();

        when(widgetConfigRepository.findById(chatbotId)).thenReturn(Optional.of(widget));
        when(documentRepository.findByWidgetConfigId(chatbotId)).thenReturn(List.of(doc));

        widgetService.softDeleteChatbot(chatbotId);

        var inOrder = inOrder(documentService, widgetConfigRepository);
        inOrder.verify(documentService).softDeleteDocument(docId);
        inOrder.verify(widgetConfigRepository).save(widget);
    }
}
