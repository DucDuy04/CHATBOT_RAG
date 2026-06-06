import axiosInstance from "./axiosInstance";

export const analyticsApi = {
  /** GET /api/analytics/summary?from=&to=&chatbotId= */
  getSummary: async ({ from, to, chatbotId } = {}) => {
    const res = await axiosInstance.get("/api/analytics/summary", {
      params: { from, to, chatbotId },
    });
    return res.data;
  },

  /** GET /api/analytics/daily?from=&to=&chatbotId= */
  getDaily: async ({ from, to, chatbotId } = {}) => {
    const res = await axiosInstance.get("/api/analytics/daily", {
      params: { from, to, chatbotId },
    });
    return res.data;
  },

  /** GET /api/analytics/by-chatbot?from=&to= */
  getByChatbot: async ({ from, to } = {}) => {
    const res = await axiosInstance.get("/api/analytics/by-chatbot", {
      params: { from, to },
    });
    return res.data;
  },

  /** GET /api/analytics/unanswered?limit=10 */
  getUnanswered: async ({ limit = 10 } = {}) => {
    const res = await axiosInstance.get("/api/analytics/unanswered", { params: { limit } });
    return res.data;
  },

  /** GET /api/analytics/sessions?from=&to=&chatbotId=&page= */
  getSessions: async ({ from, to, chatbotId, page = 0, size = 10 } = {}) => {
    const res = await axiosInstance.get("/api/analytics/sessions", {
      params: { from, to, chatbotId, page, size },
    });
    return res.data;
  },

  /** GET /api/analytics/sessions/:id/messages */
  getSessionMessages: async (id) => {
    const res = await axiosInstance.get(`/api/analytics/sessions/${id}/messages`);
    return res.data;
  },
};
