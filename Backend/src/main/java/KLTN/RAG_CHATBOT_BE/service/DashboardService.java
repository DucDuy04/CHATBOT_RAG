package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.DashboardActivityItem;
import KLTN.RAG_CHATBOT_BE.dto.DashboardMessageVolumeItem;
import KLTN.RAG_CHATBOT_BE.dto.DashboardSummaryResponse;
import KLTN.RAG_CHATBOT_BE.dto.DashboardTopChatbotItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WidgetConfigRepository widgetConfigRepository;
    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime currentFrom = today.minusDays(6).atStartOfDay();
        LocalDateTime currentTo = today.plusDays(1).atStartOfDay();
        LocalDateTime previousFrom = today.minusDays(13).atStartOfDay();
        LocalDateTime previousTo = today.minusDays(6).atStartOfDay();

        long activeChatbots = widgetConfigRepository.countByIsActiveTrue();
        long messages7d = chatMessageRepository.countByCreatedAtBetween(currentFrom, currentTo);
        long previousMessages7d = chatMessageRepository.countByCreatedAtBetween(previousFrom, previousTo);
        long documentCount = documentRepository.count();

        return DashboardSummaryResponse.builder()
                .activeChatbots(activeChatbots)
                .activeChatbotsDelta(0.0d)
                .messages7d(messages7d)
                .messages7dDelta(calculateDelta(messages7d, previousMessages7d))
                .avgSatisfaction(null)
                .avgSatisfactionDelta(0.0d)
                .documentCount(documentCount)
                .documentCountDelta(0.0d)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DashboardMessageVolumeItem> getMessageVolume(int days) {
        LocalDate today = LocalDate.now();
        LocalDate fromDay = today.minusDays(days - 1L);

        LocalDateTime from = fromDay.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        List<ChatMessage> messages = chatMessageRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);

        Map<LocalDate, Long> countByDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            countByDate.put(fromDay.plusDays(i), 0L);
        }
        for (ChatMessage message : messages) {
            LocalDate date = message.getCreatedAt().toLocalDate();
            if (countByDate.containsKey(date)) {
                countByDate.put(date, countByDate.get(date) + 1L);
            }
        }

        List<DashboardMessageVolumeItem> items = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> entry : countByDate.entrySet()) {
            items.add(DashboardMessageVolumeItem.builder()
                    .date(entry.getKey().toString())
                    .count(entry.getValue())
                    .build());
        }
        return items;
    }

    @Transactional(readOnly = true)
    public List<DashboardTopChatbotItem> getTopChatbots(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Object[]> rows = chatMessageRepository.findTopChatbotMessageCounts(pageable);

        List<DashboardTopChatbotItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            String name = row[1] == null ? "Unknown chatbot" : String.valueOf(row[1]);
            long messageCount = (Long) row[2];
            WidgetConfig widget = widgetConfigRepository.findById(id).orElse(null);
            result.add(DashboardTopChatbotItem.builder()
                    .id(id.toString())
                    .name(name)
                    .messageCount(messageCount)
                    .satisfaction(null)
                    .domain(readDomain(widget))
                    .status(widget != null && widget.isActive() ? "ACTIVE" : "INACTIVE")
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DashboardActivityItem> getActivity(int limit) {
        List<DashboardActivityItem> events = new ArrayList<>();

        for (ChatMessage message : chatMessageRepository.findLatestForDashboard(PageRequest.of(0, 50))) {
            String chatbotName = safeWidgetName(message.getSession().getWidgetConfig());
            events.add(DashboardActivityItem.builder()
                    .id("msg-" + message.getId())
                    .type("chat_message")
                    .actor("Guest")
                    .target(chatbotName)
                    .createdAt(message.getCreatedAt().toString())
                    .build());
        }

        for (Document document : documentRepository.findTop50ByOrderByUpdatedAtDesc()) {
            String eventType = switch (document.getStatus()) {
                case FAILED -> "document_failed";
                case PROCESSING -> "document_uploaded";
                case PENDING -> "document_uploaded";
                case COMPLETED -> "document_indexed";
            };
            events.add(DashboardActivityItem.builder()
                    .id("doc-" + document.getId())
                    .type(eventType)
                    .actor("System")
                    .target(document.getFileName())
                    .createdAt((document.getUpdatedAt() != null ? document.getUpdatedAt() : document.getCreatedAt()).toString())
                    .build());
        }

        for (WidgetConfig widget : widgetConfigRepository.findTop50ByOrderByUpdatedAtDesc()) {
            String type = widget.getCreatedAt() != null && widget.getUpdatedAt() != null
                    && widget.getUpdatedAt().isAfter(widget.getCreatedAt().plusSeconds(1))
                    ? "chatbot_updated"
                    : "chatbot_created";
            if (!widget.isActive()) {
                type = "chatbot_deactivated";
            }
            events.add(DashboardActivityItem.builder()
                    .id("bot-" + widget.getId())
                    .type(type)
                    .actor("Admin")
                    .target(widget.getName())
                    .createdAt((widget.getUpdatedAt() != null ? widget.getUpdatedAt() : widget.getCreatedAt()).toString())
                    .build());
        }

        return events.stream()
                .sorted(Comparator.comparing(DashboardActivityItem::getCreatedAt).reversed())
                .limit(limit)
                .toList();
    }

    private Double calculateDelta(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? 100.0d : 0.0d;
        }
        return Math.round((((double) current - previous) / previous) * 1000.0d) / 10.0d;
    }

    private String readDomain(WidgetConfig widget) {
        if (widget == null || widget.getUiConfig() == null) {
            return null;
        }
        Object raw = widget.getUiConfig().get("domain");
        if (raw == null) {
            return null;
        }
        String domain = String.valueOf(raw).trim();
        return domain.isEmpty() ? null : domain;
    }

    private String safeWidgetName(WidgetConfig widget) {
        if (widget == null || widget.getName() == null || widget.getName().isBlank()) {
            return "Unknown chatbot";
        }
        return widget.getName();
    }
}
