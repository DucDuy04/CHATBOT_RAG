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
          body: JSON.stringify({ chatbotId, message, sessionId, overrideParams }),
        });

        if (!response.ok) {
          const text = await response.text().catch(() => "");
          const data = JSON.parse(text || "{}");
          throw new Error(data.message || `HTTP ${response.status}`);
        }

        const reader  = response.body.getReader();
        const decoder = new TextDecoder();
        let   buffer  = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const events = buffer.split("\n\n");
          buffer = events.pop();

          for (const eventStr of events) {
            if (!eventStr.trim()) continue;
            const lines     = eventStr.split("\n");
            const eventLine = lines.find((l) => l.startsWith("event:"));
            const dataLine  = lines.find((l) => l.startsWith("data:"));
            if (!eventLine || !dataLine) continue;

            const eventName = eventLine.replace("event:", "").trim();
            const eventData = dataLine.startsWith("data: ")
              ? dataLine.slice(6)
              : dataLine.slice(5);

            if (eventName === "token") {
              let tokenText = eventData;
              try { tokenText = JSON.parse(eventData).token; } catch { /* keep raw */ }
              onToken(tokenText);
            } else if (eventName === "done") {
              let result = {};
              try { result = JSON.parse(eventData); } catch { /* ignore */ }
              onDone(result);
            }
          }
        }
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
  exportSession: async (sessionId) => {
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
      responseType: "blob",
    });
    return res.data;
  },
};
