import axiosInstance from "./axiosInstance";

export const chatbotsApi = {
  /**
   * GET /api/chatbots?search=&status=&domain=&page=&size=
   * Không trả status=DELETED theo mặc định.
   */
  getChatbots: async ({ search = "", status = "", domain = "", page = 0, size = 10 } = {}) => {
    const res = await axiosInstance.get("/api/chatbots", {
      params: { search, status, domain, page, size },
    });
    return res.data;
  },

  /** POST /api/chatbots */
  createChatbot: async ({ name, description, domain }) => {
    const res = await axiosInstance.post("/api/chatbots", { name, description, domain });
    return res.data;
  },

  /** GET /api/chatbots/:id */
  getChatbot: async (id) => {
    const res = await axiosInstance.get(`/api/chatbots/${id}`);
    return res.data;
  },

  /** PUT /api/chatbots/:id — payload: { name, description, status, systemPrompt, modelConfig } */
  updateChatbot: async (id, payload) => {
    const res = await axiosInstance.put(`/api/chatbots/${id}`, payload);
    return res.data;
  },

  /** DELETE /api/chatbots/:id — soft delete */
  deleteChatbot: async (id) => {
    const res = await axiosInstance.delete(`/api/chatbots/${id}`);
    return res.data;
  },

  /** GET /api/chatbots/:id/embed-config */
  getEmbedConfig: async (id) => {
    const res = await axiosInstance.get(`/api/chatbots/${id}/embed-config`);
    return res.data;
  },

  /** PUT /api/chatbots/:id/embed-config — payload: { widgetColor, welcomeMessage, position, allowedOrigins } */
  updateEmbedConfig: async (id, payload) => {
    const res = await axiosInstance.put(`/api/chatbots/${id}/embed-config`, payload);
    return res.data;
  },
};
