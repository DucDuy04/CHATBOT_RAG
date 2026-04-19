package KLTN.RAG_CHATBOT_BE.config;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WidgetAuthFilter extends OncePerRequestFilter {

    private final WidgetConfigRepository widgetRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // CHỈ KIỂM TRA API KEY VỚI ENDPOINT CHAT CỦA WIDGET BÊN NGOÀI
        if (!path.startsWith("/api/chat")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lấy API Key từ Header "X-Widget-Key"
        String apiKeyStr = request.getHeader("X-Widget-Key");

        if (apiKeyStr == null || apiKeyStr.isEmpty()) {
            sendError(response, "Missing X-Widget-Key header.");
            return;
        }

        try {
            UUID apiKey = UUID.fromString(apiKeyStr);
            Optional<WidgetConfig> widgetOpt = widgetRepository.findByApiKey(apiKey);

            if (widgetOpt.isEmpty() || !widgetOpt.get().isActive()) {
                sendError(response, "Invalid or inactive Widget Key.");
                return;
            }

            // [Tùy chọn cho sau này] Kiểm tra Origin để chống CORS giả mạo
            // String origin = request.getHeader("Origin");
            // check allowedOrigin...

            // ĐÍNH KÈM WIDGET ID VÀO REQUEST CHUYỂN TIẾP CHO CONTROLLER
            request.setAttribute("Widget-Id", widgetOpt.get().getId());
            // Backward compatibility cho code cũ nếu có chỗ đang đọc key này
            request.setAttribute("X-Widget-Id", widgetOpt.get().getId());
            
        } catch (IllegalArgumentException e) {
            sendError(response, "Invalid UUID format for Widget Key.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + msg + "\"}");
    }
}
