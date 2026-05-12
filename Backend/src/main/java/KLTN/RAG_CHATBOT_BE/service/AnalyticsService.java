package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatFeedbackRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSession;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSessionRepository;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsByChatbotItem;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsDailyItem;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSessionItem;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSessionMessageResponse;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSessionPageResponse;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSessionSourceResponse;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSummaryResponse;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsUnansweredItem;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final List<String> UNANSWERED_HINTS = List.of(
            "khong biet",
            "khong tim thay",
            "khong co thong tin",
            "i don't know",
            "not found",
            "no relevant"
    );

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatFeedbackRepository chatFeedbackRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate, UUID chatbotId) {
        TimeWindow current = toWindow(fromDate, toDate);
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDate previousFromDate = fromDate.minusDays(days);
        LocalDate previousToDate = fromDate.minusDays(1);
        TimeWindow previous = toWindow(previousFromDate, previousToDate);

        List<ChatMessage> currentMessages = chatMessageRepository.findForAnalyticsRange(current.from, current.to, chatbotId);
        List<ChatMessage> previousMessages = chatMessageRepository.findForAnalyticsRange(previous.from, previous.to, chatbotId);

        long totalMessages = currentMessages.size();
        long previousTotalMessages = previousMessages.size();
        long uniqueSessions = distinctSessionCount(currentMessages);
        long previousUniqueSessions = distinctSessionCount(previousMessages);
        SatisfactionStats currentSatisfaction = getSatisfactionStats(current, chatbotId);
        SatisfactionStats previousSatisfaction = getSatisfactionStats(previous, chatbotId);

        return AnalyticsSummaryResponse.builder()
                .totalMessages(totalMessages)
                .totalMessagesDelta(calculateDelta(totalMessages, previousTotalMessages))
                .uniqueSessions(uniqueSessions)
                .uniqueSessionsDelta(calculateDelta(uniqueSessions, previousUniqueSessions))
                .avgSatisfaction(currentSatisfaction.avgSatisfaction)
                .avgSatisfactionDelta(calculatePointDelta(
                        currentSatisfaction.avgSatisfaction,
                        previousSatisfaction.avgSatisfaction
                ))
                .fallbackRate(0.0d)
                .fallbackRateDelta(0.0d)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDailyItem> getDaily(LocalDate fromDate, LocalDate toDate, UUID chatbotId) {
        TimeWindow window = toWindow(fromDate, toDate);
        List<ChatMessage> messages = chatMessageRepository.findForAnalyticsRange(window.from, window.to, chatbotId);

        Map<LocalDate, Long> messageCounts = new LinkedHashMap<>();
        Map<LocalDate, Set<UUID>> sessionSets = new HashMap<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            messageCounts.put(cursor, 0L);
            sessionSets.put(cursor, new HashSet<>());
            cursor = cursor.plusDays(1);
        }

        for (ChatMessage message : messages) {
            LocalDate day = message.getCreatedAt().toLocalDate();
            if (!messageCounts.containsKey(day)) {
                continue;
            }
            messageCounts.put(day, messageCounts.get(day) + 1L);
            sessionSets.get(day).add(message.getSession().getId());
        }

        List<AnalyticsDailyItem> items = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> entry : messageCounts.entrySet()) {
            items.add(AnalyticsDailyItem.builder()
                    .date(entry.getKey().toString())
                    .messages(entry.getValue())
                    .sessions((long) sessionSets.get(entry.getKey()).size())
                    .build());
        }
        return items;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsByChatbotItem> getByChatbot(LocalDate fromDate, LocalDate toDate) {
        TimeWindow window = toWindow(fromDate, toDate);
        List<ChatMessage> messages = chatMessageRepository.findForAnalyticsRange(window.from, window.to, null);
        long total = messages.size();
        if (total == 0) {
            return List.of();
        }

        Map<UUID, Counter> byBot = new LinkedHashMap<>();
        for (ChatMessage message : messages) {
            UUID widgetId = message.getSession().getWidgetConfig().getId();
            Counter counter = byBot.computeIfAbsent(widgetId, key -> new Counter(
                    widgetId,
                    safeBotName(message),
                    0L
            ));
            counter.count += 1;
        }

        return byBot.values().stream()
                .sorted(Comparator.comparingLong((Counter c) -> c.count).reversed())
                .map(c -> AnalyticsByChatbotItem.builder()
                        .chatbotId(c.widgetId.toString())
                        .chatbotName(c.chatbotName)
                        .messageCount(c.count)
                        .share(roundOneDecimal((c.count * 100.0d) / total))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsUnansweredItem> getUnanswered(int limit) {
        List<ChatMessage> latest = chatMessageRepository.findLatestForDashboard(PageRequest.of(0, 500));
        if (latest.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> timeline = latest.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .toList();

        Map<String, Long> questionCounts = new HashMap<>();
        Map<String, String> questionChatbot = new HashMap<>();

        for (int i = 0; i < timeline.size() - 1; i++) {
            ChatMessage userMessage = timeline.get(i);
            ChatMessage assistantMessage = timeline.get(i + 1);
            if (userMessage.getRole() == null || assistantMessage.getRole() == null) {
                continue;
            }
            if (!"USER".equals(userMessage.getRole().name()) || !"ASSISTANT".equals(assistantMessage.getRole().name())) {
                continue;
            }
            if (!userMessage.getSession().getId().equals(assistantMessage.getSession().getId())) {
                continue;
            }
            if (!looksUnanswered(assistantMessage.getContent())) {
                continue;
            }
            String question = normalizeQuestion(userMessage.getContent());
            if (question.isBlank()) {
                continue;
            }
            questionCounts.put(question, questionCounts.getOrDefault(question, 0L) + 1L);
            questionChatbot.putIfAbsent(question, safeBotName(userMessage));
        }

        return questionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> AnalyticsUnansweredItem.builder()
                        .id("uq-" + Integer.toHexString(entry.getKey().hashCode()))
                        .question(entry.getKey())
                        .chatbotName(questionChatbot.getOrDefault(entry.getKey(), "Unknown chatbot"))
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalyticsSessionPageResponse getSessions(
            LocalDate fromDate,
            LocalDate toDate,
            UUID chatbotId,
            String rating,
            int page,
            int size
    ) {
        String normalizedRating = rating == null ? "" : rating.trim().toLowerCase(Locale.ROOT);
        TimeWindow window = toWindow(fromDate, toDate);
        boolean needsInMemoryRatingFilter = "positive".equals(normalizedRating)
                || "negative".equals(normalizedRating)
                || "unrated".equals(normalizedRating);
        if (needsInMemoryRatingFilter) {
            List<ChatSession> allSessions = chatSessionRepository.findAllForAnalyticsRange(window.from, window.to, chatbotId);
            return buildFilteredSessionPage(allSessions, normalizedRating, page, size);
        }

        // "all", empty, unsupported rating values -> same as all sessions.
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatSession> sessionsPage = chatSessionRepository.findForAnalyticsRange(window.from, window.to, chatbotId, pageable);
        return buildUnfilteredSessionPage(sessionsPage);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsSessionMessageResponse> getSessionMessages(UUID sessionKey) {
        ChatSession session = chatSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(message -> AnalyticsSessionMessageResponse.builder()
                        .id(message.getId().toString())
                        .role(message.getRole() == null ? "user" : message.getRole().name().toLowerCase(Locale.ROOT))
                        .content(message.getContent())
                        .createdAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toString())
                        .sources(mapSources(message))
                        .build())
                .toList();
    }

    private TimeWindow toWindow(LocalDate fromDate, LocalDate toDate) {
        return TimeWindow.builder()
                .from(fromDate.atStartOfDay())
                .to(toDate.plusDays(1).atStartOfDay())
                .build();
    }

    private long distinctSessionCount(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> message.getSession().getId())
                .distinct()
                .count();
    }

    private double calculateDelta(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? 100.0d : 0.0d;
        }
        return roundOneDecimal(((current - previous) * 100.0d) / previous);
    }

    private double calculatePointDelta(Double current, Double previous) {
        if (current == null && previous == null) {
            return 0.0d;
        }
        if (current == null) {
            return roundOneDecimal(-previous);
        }
        if (previous == null) {
            return roundOneDecimal(current);
        }
        return roundOneDecimal(current - previous);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    private String safeBotName(ChatMessage message) {
        String name = message.getSession().getWidgetConfig().getName();
        if (name == null || name.isBlank()) {
            return "Unknown chatbot";
        }
        return name;
    }

    private boolean looksUnanswered(String assistantText) {
        if (assistantText == null || assistantText.isBlank()) {
            return false;
        }
        String normalized = normalizeText(assistantText);
        for (String hint : UNANSWERED_HINTS) {
            if (normalized.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.trim();
    }

    private String normalizeText(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized
                .replace('đ', 'd')
                .replaceAll("[áàảãạăắằẳẵặâấầẩẫậ]", "a")
                .replaceAll("[éèẻẽẹêếềểễệ]", "e")
                .replaceAll("[íìỉĩị]", "i")
                .replaceAll("[óòỏõọôốồổỗộơớờởỡợ]", "o")
                .replaceAll("[úùủũụưứừửữự]", "u")
                .replaceAll("[ýỳỷỹỵ]", "y");
    }

    private List<AnalyticsSessionSourceResponse> mapSources(ChatMessage message) {
        if (message.getSources() == null || message.getSources().isEmpty()) {
            return List.of();
        }
        List<AnalyticsSessionSourceResponse> sources = new ArrayList<>();
        for (Map<String, Object> source : message.getSources()) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            sources.add(AnalyticsSessionSourceResponse.builder()
                    .fileName(asString(source.get("fileName")))
                    .documentName(asString(source.get("documentName")))
                    .title(asString(source.get("title")))
                    .score(asDouble(source.get("score")))
                    .snippet(asString(source.get("snippet")))
                    .chunk(asString(source.get("chunk")))
                    .content(asString(source.get("content")))
                    .pages(asString(source.get("pages")))
                    .build());
        }
        return sources;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String out = String.valueOf(value).trim();
        return out.isEmpty() ? null : out;
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AnalyticsSessionPageResponse buildUnfilteredSessionPage(Page<ChatSession> sessionsPage) {
        List<AnalyticsSessionItem> items = toSessionItems(sessionsPage.getContent(), buildSessionRatingMap(sessionsPage.getContent()));
        return AnalyticsSessionPageResponse.builder()
                .items(items)
                .page(sessionsPage.getNumber())
                .size(sessionsPage.getSize())
                .total(sessionsPage.getTotalElements())
                .totalPages(sessionsPage.getTotalPages())
                .build();
    }

    private AnalyticsSessionPageResponse buildFilteredSessionPage(
            List<ChatSession> allSessions,
            String normalizedRating,
            int page,
            int size
    ) {
        Map<UUID, Integer> sessionRatingMap = buildSessionRatingMap(allSessions);
        List<ChatSession> filtered = allSessions.stream()
                .filter(session -> matchesRatingFilter(sessionRatingMap.get(session.getId()), normalizedRating))
                .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<ChatSession> pageItems = filtered.subList(fromIndex, toIndex);
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);

        return AnalyticsSessionPageResponse.builder()
                .items(toSessionItems(pageItems, sessionRatingMap))
                .page(page)
                .size(size)
                .total((long) filtered.size())
                .totalPages(totalPages)
                .build();
    }

    private List<AnalyticsSessionItem> toSessionItems(List<ChatSession> sessions, Map<UUID, Integer> sessionRatingMap) {
        return sessions.stream()
                .map(session -> AnalyticsSessionItem.builder()
                        .id(session.getSessionKey().toString())
                        .chatbotId(session.getWidgetConfig().getId().toString())
                        .chatbotName(session.getWidgetConfig().getName())
                        .messageCount(chatMessageRepository.countBySessionId(session.getId()))
                        .rating(sessionRatingMap.get(session.getId()))
                        .createdAt(session.getCreatedAt() == null ? null : session.getCreatedAt().toString())
                        .build())
                .toList();
    }

    private Map<UUID, Integer> buildSessionRatingMap(List<ChatSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        List<UUID> sessionIds = sessions.stream()
                .map(ChatSession::getId)
                .toList();
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : chatFeedbackRepository.aggregateBySessionIds(sessionIds)) {
            UUID sessionId = (UUID) row[0];
            long positive = row[1] == null ? 0L : ((Number) row[1]).longValue();
            long negative = row[2] == null ? 0L : ((Number) row[2]).longValue();
            if (positive > negative) {
                map.put(sessionId, 1);
            } else if (negative > positive) {
                map.put(sessionId, -1);
            } else {
                map.put(sessionId, null);
            }
        }
        return map;
    }

    private boolean matchesRatingFilter(Integer rating, String normalizedRating) {
        return switch (normalizedRating) {
            case "positive" -> Integer.valueOf(1).equals(rating);
            case "negative" -> Integer.valueOf(-1).equals(rating);
            case "unrated" -> rating == null;
            default -> true;
        };
    }

    private SatisfactionStats getSatisfactionStats(TimeWindow window, UUID chatbotId) {
        long totalFeedback = chatFeedbackRepository.countInRange(window.from, window.to, chatbotId);
        if (totalFeedback <= 0) {
            return new SatisfactionStats(0L, 0L, null);
        }
        long positiveFeedback = chatFeedbackRepository.countByRatingInRange(window.from, window.to, chatbotId, 1);
        double avg = roundOneDecimal((positiveFeedback * 100.0d) / totalFeedback);
        return new SatisfactionStats(totalFeedback, positiveFeedback, avg);
    }

    @Value
    @Builder
    private static class TimeWindow {
        LocalDateTime from;
        LocalDateTime to;
    }

    private static class Counter {
        private final UUID widgetId;
        private final String chatbotName;
        private long count;

        private Counter(UUID widgetId, String chatbotName, long count) {
            this.widgetId = widgetId;
            this.chatbotName = chatbotName;
            this.count = count;
        }
    }

    private record SatisfactionStats(long total, long positive, Double avgSatisfaction) {
    }
}
