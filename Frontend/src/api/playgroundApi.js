import axiosInstance from "./axiosInstance";
import { USE_MOCK_API, mockDelay } from "./apiMode";
import { sessions, messagesBySession, mockStreamTokens, MOCK_SOURCES } from "../mocks/playgroundMock";

const API_BASE = (import.meta.env.VITE_API_URL || "").trim();

let _nextSessionId = 4;
let _nextMsgId = 20;

export const playgroundApi = {
  /**
   * POST /api/playground/chat — SSE stream
   * Callbacks: onToken(text), onDone(result), onError(error)
   * Returns an abort controller so caller can cancel.
   */
  chat: ({ chatbotId, message, sessionId, overrideParams = {}, onToken, onDone, onError }) => {
    if (USE_MOCK_API) {
      const controller = { abort: () => {} };

      (async () => {
        try {
          await mockDelay(500);
          let answer = "";
          for (const token of mockStreamTokens) {
            await mockDelay(80);
            answer += token;
            onToken(token);
          }
          onDone({
            messageId: `msg-mock-${_nextMsgId++}`,
            answer: answer.trim(),
            sources: MOCK_SOURCES,
            retrieval: { topK: 5, rerankEnabled: false },
            latency: 1350,
            sessionId: sessionId || `sess-mock-${_nextSessionId}`,
          });
        } catch (err) {
          onError(err);
        }
      })();

      return controller;
    }

    // Real SSE streaming
    const abortController = new AbortController();
    const token = localStorage.getItem("auth_token");

    (async () => {
      try {
        const response = await fetch(`${API_BASE}/api/playground/chat`, {
          method: "POST",
          signal: abortController.signal,
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify({
            chatbotId,
            message,
            sessionId,
            overrideParams,
            topK:
              overrideParams?.topK != null && Number.isFinite(Number(overrideParams.topK))
                ? Math.min(30, Math.max(1, Math.floor(Number(overrideParams.topK))))
                : undefined,
            temperature:
              overrideParams?.temperature != null && Number.isFinite(Number(overrideParams.temperature))
                ? Math.min(1, Math.max(0, Number(overrideParams.temperature)))
                : undefined,
            maxTokens:
              overrideParams?.maxTokens != null && Number.isFinite(Number(overrideParams.maxTokens))
                ? Math.min(4096, Math.max(64, Math.floor(Number(overrideParams.maxTokens))))
                : undefined,
          }),
        });

        if (!response.ok) {
          const text = await response.text().catch(() => "");
          const data = JSON.parse(text || "{}");
          throw new Error(data.message || `HTTP ${response.status}`);
        }

        const reader  = response.body.getReader();
        const decoder = new TextDecoder();
        let   buffer  = "";

        const processEventBlock = (eventStr) => {
          if (!eventStr || !eventStr.trim()) return;
          const normalized = eventStr.replace(/\r/g, "");
          const lines = normalized.split("\n");
          const eventLine = lines.find((l) => l.startsWith("event:"));
          const dataLines = lines.filter((l) => l.startsWith("data:"));
          if (!eventLine || dataLines.length === 0) return;

          const eventName = eventLine.replace("event:", "").trim();
          const eventData = dataLines
            .map((line) => (line.startsWith("data: ") ? line.slice(6) : line.slice(5)))
            .join("\n");

          if (eventName === "token") {
            let tokenText = eventData;
            try {
              const parsed = JSON.parse(eventData);
              tokenText = typeof parsed?.token === "string" ? parsed.token : eventData;
            } catch {
              // Keep raw payload as fallback
            }
            onToken?.(tokenText);
          } else if (eventName === "done") {
            let result = {};
            try {
              result = JSON.parse(eventData);
            } catch {
              result = {};
            }
            // Backend currently sends done as an array of sources.
            if (Array.isArray(result)) {
              onDone?.({ sources: result });
              return;
            }
            onDone?.(result);
          }
        };

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const events = buffer.split("\n\n");
          buffer = events.pop();

          for (const eventStr of events) processEventBlock(eventStr);
        }
        // Flush any trailing event block that may not end with "\n\n".
        processEventBlock(buffer);
      } catch (err) {
        if (err?.name !== "AbortError") onError(err);
      }
    })();

    return abortController;
  },

  /** POST /api/playground/compare — returns { configA: result, configB: result } */
  compare: async ({ chatbotId, message, configA = {}, configB = {} }) => {
    if (USE_MOCK_API) {
      await mockDelay(1200);
      const mockResult = (label) => ({
        answer: `[${label}] Đây là câu trả lời mock với config ${label}. Nội dung phản hồi phụ thuộc vào temperature và model được chọn trong cấu hình.`,
        sources: MOCK_SOURCES,
        latency: 900 + Math.floor(Math.random() * 600),
      });
      return { configA: mockResult("A"), configB: mockResult("B") };
    }
    const res = await axiosInstance.post("/api/playground/compare", {
      chatbotId, message, configA, configB,
    });
    return res.data;
  },

  /** GET /api/playground/sessions?chatbotId= */
  getSessions: async (chatbotId) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      if (chatbotId) return sessions.filter((s) => s.chatbotId === chatbotId);
      return [...sessions];
    }
    const res = await axiosInstance.get("/api/playground/sessions", {
      params: chatbotId ? { chatbotId } : {},
    });
    return res.data;
  },

  /** DELETE /api/playground/sessions/:id */
  deleteSession: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(250);
      const idx = sessions.findIndex((s) => s.id === id);
      if (idx >= 0) sessions.splice(idx, 1);
      return { success: true };
    }
    const res = await axiosInstance.delete(`/api/playground/sessions/${id}`);
    return res.data;
  },

  /** GET /api/playground/export/:sessionId — returns exportable data */
  exportSession: async (sessionId, { asBlob = false } = {}) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      const msgs = messagesBySession[sessionId] || [];
      return {
        sessionId,
        exportedAt: new Date().toISOString(),
        messages: msgs,
      };
    }
    const res = await axiosInstance.get(`/api/playground/export/${sessionId}`, {
      responseType: asBlob ? "blob" : "json",
    });
    return res.data;
  },
};
