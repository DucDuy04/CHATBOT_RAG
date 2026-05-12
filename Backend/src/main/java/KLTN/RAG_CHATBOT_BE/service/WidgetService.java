package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatbotCreateRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatbotPageResponse;
import KLTN.RAG_CHATBOT_BE.dto.ChatbotResponse;
import KLTN.RAG_CHATBOT_BE.dto.ChatbotUpdateRequest;
import KLTN.RAG_CHATBOT_BE.dto.EmbedConfigResponse;
import KLTN.RAG_CHATBOT_BE.dto.EmbedConfigUpdateRequest;
import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateRequest;
import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WidgetService {

    public static final String DEFAULT_ALLOWED_ORIGIN = "http://localhost:5173";

    private static final Map<String, Object> DEFAULT_MODEL_CONFIG = Map.of(
            "model", "GPT-4o",
            "temperature", 0.7,
            "topK", 5,
            "maxTokens", 1024
    );

    private static final String DEFAULT_WIDGET_COLOR = "#2563eb";
    private static final String DEFAULT_WELCOME = "Xin chào! Tôi có thể giúp gì cho bạn?";
    private static final String DEFAULT_POSITION = "bottom-right";
    private static final String DEFAULT_LAUNCHER_ICON = "chat";

    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$");

    private final WidgetConfigRepository repository;
    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;

    public WidgetCreateResponse createWidgetConfig(WidgetCreateRequest request) {
        validateRequest(request);

        List<String> normalizedAllowedOrigin = request.getAllowedOrigin()
                .stream()
                .map(String::trim)
                .toList();

        WidgetConfig widget = WidgetConfig.builder()
                .name(request.getName().trim())
                .allowedOrigin(normalizedAllowedOrigin)
                .uiConfig(request.getUiConfig() == null ? Collections.emptyMap() : request.getUiConfig())
                .apiKey(UUID.randomUUID())
                .isActive(true)
                .build();

        WidgetConfig saved = repository.save(widget);

        return WidgetCreateResponse.builder()
                .widgetConfigId(saved.getId())
                .apiKey(saved.getApiKey())
                .name(saved.getName())
                .allowedOrigin(saved.getAllowedOrigin())
                .uiConfig(saved.getUiConfig())
                .uploadEndpoint("/api/documents/upload/" + saved.getId())
                .message("Tạo widgetConfig thành công. Dùng widgetConfigId này để ingest tài liệu.")
                .build();
    }

    /**
     * Create chatbot (FE) — reuse widget create; description/domain trong {@code uiConfig}.
     */
    @Transactional
    public ChatbotResponse createChatbot(ChatbotCreateRequest request) {
        WidgetCreateRequest widgetRequest = new WidgetCreateRequest();
        widgetRequest.setName(request.getName());
        widgetRequest.setAllowedOrigin(List.of(DEFAULT_ALLOWED_ORIGIN));

        Map<String, Object> uiConfig = new LinkedHashMap<>();
        uiConfig.put("description", request.getDescription() == null ? "" : request.getDescription());
        uiConfig.put("domain", request.getDomain() == null || request.getDomain().isBlank()
                ? "general"
                : request.getDomain());
        widgetRequest.setUiConfig(uiConfig);

        WidgetCreateResponse created = createWidgetConfig(widgetRequest);
        return repository.findById(created.getWidgetConfigId())
                .map(w -> toChatbotResponse(w, true))
                .orElseThrow(() -> new IllegalStateException("Không load lại được chatbot vừa tạo."));
    }

    @Transactional(readOnly = true)
    public ChatbotPageResponse listChatbots(String search, String status, String domain, int page, int size) {
        String s = search == null ? "" : search.trim();
        String st = normalizeStatusFilter(status);
        String d = domain == null ? "" : domain.trim();

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<WidgetConfig> result = repository.searchChatbots(s, st, d, pageable);

        List<ChatbotResponse> items = result.getContent().stream()
                .map(w -> toChatbotResponse(w, false))
                .toList();

        return ChatbotPageResponse.builder()
                .items(items)
                .page(result.getNumber())
                .size(result.getSize())
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotResponse> getChatbot(UUID id) {
        return repository.findById(id).map(w -> toChatbotResponse(w, false));
    }

    @Transactional
    public Optional<ChatbotResponse> updateChatbot(UUID id, ChatbotUpdateRequest req) {
        return repository.findById(id).map(w -> {
            if (req.getStatus() != null && !req.getStatus().isBlank()
                    && "DELETED".equalsIgnoreCase(req.getStatus().trim())) {
                throw new IllegalArgumentException("Không chấp nhận status DELETED qua PUT.");
            }

            if (req.getName() != null && !req.getName().isBlank()) {
                w.setName(req.getName().trim());
            }

            Map<String, Object> ui = mutableUi(w);

            if (req.getDescription() != null) {
                ui.put("description", req.getDescription());
            }
            if (req.getDomain() != null && !req.getDomain().isBlank()) {
                ui.put("domain", req.getDomain().trim());
            }
            if (req.getSystemPrompt() != null) {
                ui.put("systemPrompt", req.getSystemPrompt());
            }

            if (req.getModelConfig() != null && !req.getModelConfig().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = (Map<String, Object>) ui.get("modelConfig");
                Map<String, Object> merged = new LinkedHashMap<>();
                if (existing != null) {
                    merged.putAll(existing);
                }
                merged.putAll(req.getModelConfig());
                ui.put("modelConfig", merged);
            }

            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                String st = req.getStatus().trim().toUpperCase(Locale.ROOT);
                if ("ACTIVE".equals(st)) {
                    w.setActive(true);
                    ui.put("status", "ACTIVE");
                } else if ("INACTIVE".equals(st)) {
                    w.setActive(false);
                    ui.put("status", "INACTIVE");
                } else {
                    throw new IllegalArgumentException("status phải là ACTIVE hoặc INACTIVE.");
                }
            }

            w.setUiConfig(ui);
            WidgetConfig saved = repository.save(w);
            return toChatbotResponse(saved, false);
        });
    }

    @Transactional
    public boolean softDeleteChatbot(UUID id) {
        Optional<WidgetConfig> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        WidgetConfig w = opt.get();
        w.setDeletedAt(LocalDateTime.now());
        w.setActive(false);
        Map<String, Object> ui = mutableUi(w);
        ui.put("status", "DELETED");
        w.setUiConfig(ui);
        repository.save(w);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<EmbedConfigResponse> getEmbedConfig(UUID id) {
        return repository.findById(id).map(this::toEmbedConfigResponse);
    }

    @Transactional
    public Optional<EmbedConfigResponse> updateEmbedConfig(UUID id, EmbedConfigUpdateRequest req) {
        return repository.findById(id).map(w -> {
            Map<String, Object> ui = mutableUi(w);

            if (req.getWidgetColor() != null) {
                String wc = req.getWidgetColor().trim();
                if (wc.isEmpty()) {
                    ui.put("widgetColor", DEFAULT_WIDGET_COLOR);
                } else if (!HEX_COLOR.matcher(wc).matches()) {
                    throw new IllegalArgumentException("widgetColor phải là mã hex #RGB hoặc #RRGGBB.");
                } else {
                    ui.put("widgetColor", wc);
                }
            }

            if (req.getWelcomeMessage() != null) {
                ui.put("welcomeMessage", req.getWelcomeMessage());
            }

            if (req.getPosition() != null && !req.getPosition().isBlank()) {
                String pos = req.getPosition().trim();
                if (!"bottom-right".equals(pos) && !"bottom-left".equals(pos)) {
                    throw new IllegalArgumentException("position chỉ được bottom-right hoặc bottom-left.");
                }
                ui.put("position", pos);
            }

            if (req.getLauncherIcon() != null && !req.getLauncherIcon().isBlank()) {
                ui.put("launcherIcon", req.getLauncherIcon().trim());
            }

            if (req.getAllowedOrigins() != null) {
                List<String> normalized = req.getAllowedOrigins().stream()
                        .filter(o -> o != null && !o.isBlank())
                        .map(String::trim)
                        .toList();
                w.setAllowedOrigin(normalized);
            }

            w.setUiConfig(ui);
            WidgetConfig saved = repository.save(w);
            return toEmbedConfigResponse(saved);
        });
    }

    private EmbedConfigResponse toEmbedConfigResponse(WidgetConfig w) {
        Map<String, Object> ui = uiMap(w);
        String widgetColor = stringFrom(ui, "widgetColor", DEFAULT_WIDGET_COLOR);
        String welcome = stringFrom(ui, "welcomeMessage", DEFAULT_WELCOME);
        String position = stringFrom(ui, "position", DEFAULT_POSITION);
        if (!"bottom-right".equals(position) && !"bottom-left".equals(position)) {
            position = DEFAULT_POSITION;
        }
        String launcher = stringFrom(ui, "launcherIcon", DEFAULT_LAUNCHER_ICON);

        List<String> origins = w.getAllowedOrigin() != null
                ? List.copyOf(w.getAllowedOrigin())
                : List.of();

        return EmbedConfigResponse.builder()
                .widgetKey(w.getApiKey() != null ? w.getApiKey().toString() : null)
                .widgetColor(widgetColor)
                .welcomeMessage(welcome)
                .position(position)
                .allowedOrigins(origins)
                .launcherIcon(launcher)
                .build();
    }

    private ChatbotResponse toChatbotResponse(WidgetConfig w, boolean includeApiKey) {
        Map<String, Object> ui = uiMap(w);

        String description = stringFrom(ui, "description", "");
        String domain = stringFrom(ui, "domain", "");
        if (domain.isEmpty()) {
            domain = "general";
        }

        String systemPrompt = stringFrom(ui, "systemPrompt", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> modelRaw = (Map<String, Object>) ui.get("modelConfig");
        Map<String, Object> modelConfig = mergeModelConfig(modelRaw);

        long docCount = documentRepository.countByWidgetConfig_Id(w.getId());
        long msgCount = chatMessageRepository.countByWidgetConfigId(w.getId());

        String status = w.isActive() ? "ACTIVE" : "INACTIVE";

        String updatedAt;
        if (w.getUpdatedAt() != null) {
            updatedAt = w.getUpdatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
        } else if (w.getCreatedAt() != null) {
            updatedAt = w.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
        } else {
            updatedAt = OffsetDateTime.now().toString();
        }

        ChatbotResponse.ChatbotResponseBuilder b = ChatbotResponse.builder()
                .id(w.getId().toString())
                .name(w.getName())
                .description(description)
                .domain(domain)
                .documentCount((int) Math.min(docCount, Integer.MAX_VALUE))
                .messageCount((int) Math.min(msgCount, Integer.MAX_VALUE))
                .status(status)
                .updatedAt(updatedAt)
                .initials(toInitials(w.getName()))
                .systemPrompt(systemPrompt)
                .modelConfig(modelConfig);

        if (includeApiKey) {
            b.apiKey(w.getApiKey().toString());
        }

        return b.build();
    }

    private Map<String, Object> mergeModelConfig(Map<String, Object> fromUi) {
        Map<String, Object> out = new LinkedHashMap<>(DEFAULT_MODEL_CONFIG);
        if (fromUi != null) {
            out.putAll(fromUi);
        }
        if (!out.containsKey("topK")) {
            out.put("topK", 5);
        }
        return out;
    }

    private Map<String, Object> uiMap(WidgetConfig w) {
        if (w.getUiConfig() == null || w.getUiConfig().isEmpty()) {
            return Collections.emptyMap();
        }
        return w.getUiConfig();
    }

    private Map<String, Object> mutableUi(WidgetConfig w) {
        return new LinkedHashMap<>(uiMap(w));
    }

    private String stringFrom(Map<String, Object> ui, String key, String defaultVal) {
        Object v = ui.get(key);
        if (v == null) {
            return defaultVal;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? defaultVal : s;
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String toInitials(String name) {
        if (name == null || name.isBlank()) {
            return "CB";
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return normalized.length() <= 2 ? normalized : normalized.substring(0, 2);
    }

    private void validateRequest(WidgetCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request không được để trống.");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("name là bắt buộc.");
        }
        if (request.getAllowedOrigin() == null || request.getAllowedOrigin().isEmpty()) {
            throw new IllegalArgumentException("allowedOrigin phải có ít nhất 1 domain.");
        }

        List<String> invalidOrigins = new ArrayList<>();
        for (String origin : request.getAllowedOrigin()) {
            if (origin == null || origin.trim().isEmpty()) {
                invalidOrigins.add(String.valueOf(origin));
            }
        }
        if (!invalidOrigins.isEmpty()) {
            throw new IllegalArgumentException("allowedOrigin chứa giá trị rỗng/không hợp lệ.");
        }
    }
}
