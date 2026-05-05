import axiosInstance from "./axiosInstance";
import { USE_MOCK_API, mockDelay, createPaginatedResponse } from "./apiMode";
import {
  analyticsSummary,
  dailyData,
  byChatbot,
  unansweredQuestions,
  analyticsSessions,
  sessionMessages,
} from "../mocks/analyticsMock";

export const analyticsApi = {
  /** GET /api/analytics/summary?from=&to=&chatbotId= */
  getSummary: async ({ from, to, chatbotId } = {}) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      // params are accepted but mock returns static summary
      void from; void to; void chatbotId;
      return { ...analyticsSummary };
    }
    const res = await axiosInstance.get("/api/analytics/summary", {
      params: { from, to, chatbotId },
    });
    return res.data;
  },

  /** GET /api/analytics/daily?from=&to=&chatbotId= */
  getDaily: async ({ from, to, chatbotId } = {}) => {
    if (USE_MOCK_API) {
      await mockDelay(350);
      void from; void to; void chatbotId;
      return [...dailyData];
    }
    const res = await axiosInstance.get("/api/analytics/daily", {
      params: { from, to, chatbotId },
    });
    return res.data;
  },

  /** GET /api/analytics/by-chatbot?from=&to= */
  getByChatbot: async ({ from, to } = {}) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      void from; void to;
      return [...byChatbot];
    }
    const res = await axiosInstance.get("/api/analytics/by-chatbot", {
      params: { from, to },
    });
    return res.data;
  },

  /** GET /api/analytics/unanswered?limit=10 */
  getUnanswered: async ({ limit = 10 } = {}) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return unansweredQuestions.slice(0, limit);
    }
    const res = await axiosInstance.get("/api/analytics/unanswered", { params: { limit } });
    return res.data;
  },

  /** GET /api/analytics/sessions?from=&to=&chatbotId=&rating=&page= */
  getSessions: async ({ from, to, chatbotId, rating, page = 0, size = 10 } = {}) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      let filtered = [...analyticsSessions];
      if (chatbotId) filtered = filtered.filter((s) => s.chatbotId === chatbotId);
      if (rating)    filtered = filtered.filter((s) => s.rating === Number(rating));
      void from; void to;
      return createPaginatedResponse(filtered, page, size);
    }
    const res = await axiosInstance.get("/api/analytics/sessions", {
      params: { from, to, chatbotId, rating, page, size },
    });
    return res.data;
  },

  /** GET /api/analytics/sessions/:id/messages */
  getSessionMessages: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(250);
      return sessionMessages[id] || [];
    }
    const res = await axiosInstance.get(`/api/analytics/sessions/${id}/messages`);
    return res.data;
  },

  /** POST /api/chat/feedback — body: { messageId, rating, comment } */
  submitFeedback: async ({ messageId, rating, comment = "" }) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return { success: true, messageId, rating };
    }
    const res = await axiosInstance.post("/api/chat/feedback", { messageId, rating, comment });
    return res.data;
  },
};
