package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.settings.SettingsApiKey;
import KLTN.RAG_CHATBOT_BE.domain.settings.SettingsApiKeyRepository;
import KLTN.RAG_CHATBOT_BE.domain.settings.SettingsProfile;
import KLTN.RAG_CHATBOT_BE.domain.settings.SettingsProfileRepository;
import KLTN.RAG_CHATBOT_BE.dto.SettingsApiKeyCreateRequest;
import KLTN.RAG_CHATBOT_BE.dto.SettingsApiKeyCreateResponse;
import KLTN.RAG_CHATBOT_BE.dto.SettingsApiKeyResponse;
import KLTN.RAG_CHATBOT_BE.dto.SettingsNotificationsDto;
import KLTN.RAG_CHATBOT_BE.dto.SettingsProfileResponse;
import KLTN.RAG_CHATBOT_BE.dto.SettingsProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String KEY_PREFIX = "sk_live_";
    private static final int RANDOM_PART_LEN = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SettingsProfileRepository profileRepository;
    private final SettingsApiKeyRepository apiKeyRepository;

    @Value("${app.settings.profile.default-name:Admin User}")
    private String defaultProfileName;

    @Value("${app.settings.profile.default-email:admin@example.com}")
    private String defaultProfileEmail;

    @Transactional
    public SettingsProfileResponse getProfile() {
        return toResponse(ensureProfile());
    }

    @Transactional
    public SettingsProfileResponse updateProfile(SettingsProfileUpdateRequest request) {
        if (request.getLanguage() != null) {
            String lang = request.getLanguage().trim();
            if (lang.isEmpty() || (!"vi".equals(lang) && !"en".equals(lang))) {
                throw new IllegalArgumentException("language must be vi or en");
            }
        }

        SettingsProfile p = ensureProfile();

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Full name is required");
            }
            p.setFullName(name);
        }

        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (email.isEmpty()) {
                throw new IllegalArgumentException("Invalid email");
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException("Invalid email");
            }
            p.setEmail(email);
        }

        if (request.getLanguage() != null) {
            p.setLanguage(request.getLanguage().trim());
        }

        if (request.getNotifications() != null) {
            SettingsNotificationsDto n = request.getNotifications();
            if (n.getEmbeddingFailed() != null) {
                p.setNotifyEmbeddingFailed(n.getEmbeddingFailed());
            }
            if (n.getDailySummary() != null) {
                p.setNotifyDailySummary(n.getDailySummary());
            }
            if (n.getNewFeedback() != null) {
                p.setNotifyNewFeedback(n.getNewFeedback());
            }
        }

        p = profileRepository.save(p);
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<SettingsApiKeyResponse> listApiKeys() {
        return apiKeyRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::toApiKeyResponse)
                .toList();
    }

    @Transactional
    public SettingsApiKeyCreateResponse createApiKey(SettingsApiKeyCreateRequest request) {
        String label = request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : "Generated Key";

        String tokenPart = randomAlphanumeric(RANDOM_PART_LEN);
        String plain = KEY_PREFIX + tokenPart;
        String suffix = tokenPart.substring(tokenPart.length() - 4);
        String masked = KEY_PREFIX + "****" + suffix;

        SettingsApiKey entity = SettingsApiKey.builder()
                .name(label)
                .keyHash(sha256Hex(plain))
                .keyPrefix(KEY_PREFIX)
                .keySuffix(suffix)
                .maskedKey(masked)
                .status("ACTIVE")
                .build();

        entity = apiKeyRepository.save(entity);
        SettingsApiKeyResponse dto = toApiKeyResponse(entity);
        return SettingsApiKeyCreateResponse.builder()
                .key(dto)
                .plainTextKey(plain)
                .build();
    }

    @Transactional
    public void deleteApiKey(UUID id) {
        Optional<SettingsApiKey> opt = apiKeyRepository.findByIdAndDeletedAtIsNull(id);
        if (opt.isEmpty()) {
            throw new SettingsApiKeyNotFoundException();
        }
        SettingsApiKey k = opt.get();
        k.setDeletedAt(java.time.LocalDateTime.now());
        apiKeyRepository.save(k);
    }

    private SettingsProfile ensureProfile() {
        return profileRepository.findById(SettingsProfile.SINGLETON_ID)
                .orElseGet(this::insertDefaultProfile);
    }

    private SettingsProfile insertDefaultProfile() {
        SettingsProfile created = SettingsProfile.builder()
                .id(SettingsProfile.SINGLETON_ID)
                .fullName(defaultProfileName)
                .email(defaultProfileEmail)
                .language("vi")
                .notifyEmbeddingFailed(false)
                .notifyDailySummary(false)
                .notifyNewFeedback(false)
                .build();
        return profileRepository.save(created);
    }

    private SettingsProfileResponse toResponse(SettingsProfile p) {
        return SettingsProfileResponse.builder()
                .name(p.getFullName())
                .email(p.getEmail())
                .language(p.getLanguage())
                .notifications(SettingsNotificationsDto.builder()
                        .embeddingFailed(p.isNotifyEmbeddingFailed())
                        .dailySummary(p.isNotifyDailySummary())
                        .newFeedback(p.isNotifyNewFeedback())
                        .build())
                .build();
    }

    private SettingsApiKeyResponse toApiKeyResponse(SettingsApiKey k) {
        return SettingsApiKeyResponse.builder()
                .id(k.getId().toString())
                .name(k.getName())
                .maskedKey(k.getMaskedKey())
                .status(k.getStatus())
                .createdAt(k.getCreatedAt())
                .lastUsedAt(k.getLastUsedAt())
                .build();
    }

    private static String randomAlphanumeric(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String sha256Hex(String plain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static final class SettingsApiKeyNotFoundException extends RuntimeException {
    }
}
