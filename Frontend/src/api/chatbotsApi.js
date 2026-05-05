import axiosInstance from "./axiosInstance";
import { USE_MOCK_API, mockDelay, createPaginatedResponse } from "./apiMode";
import { chatbots, embedConfigs } from "../mocks/chatbotsMock";

let _nextId = 9; // mock auto-increment

export const chatbotsApi = {
  /**
   * GET /api/chatbots?search=&status=&domain=&page=&size=
   * Không trả status=DELETED theo mặc định.
   */
  getChatbots: async ({ search = "", status = "", domain = "", page = 0, size = 10 } = {}) => {
    if (USE_MOCK_API) {
      await mockDelay(350);
      let filtered = chatbots.filter((c) => c.status !== "DELETED");
      if (search) {
        const q = search.toLowerCase();
        filtered = filtered.filter(
          (c) => c.name.toLowerCase().includes(q) || c.description.toLowerCase().includes(q)
        );
      }
      if (status) filtered = filtered.filter((c) => c.status === status);
      if (domain) filtered = filtered.filter((c) => c.domain === domain);
      return createPaginatedResponse(filtered, page, size);
    }
    const res = await axiosInstance.get("/api/chatbots", {
      params: { search, status, domain, page, size },
    });
    return res.data;
  },

  /** POST /api/chatbots */
  createChatbot: async ({ name, description, domain }) => {
    if (USE_MOCK_API) {
      await mockDelay(500);
      const newBot = {
        id: `cb-00${_nextId++}`,
        name,
        description: description || "",
        domain: domain || "general",
        documentCount: 0,
        messageCount: 0,
        status: "ACTIVE",
        updatedAt: new Date().toISOString(),
        initials: name.slice(0, 2).toUpperCase(),
        systemPrompt: "",
        modelConfig: { model: "llama-3.1-70b-versatile", temperature: 0.7, maxTokens: 1024 },
      };
      chatbots.push(newBot);
      embedConfigs[newBot.id] = {
        widgetColor: "#3b82f6",
        welcomeMessage: "Xin chào! Tôi có thể giúp gì cho bạn?",
        position: "bottom-right",
        allowedOrigins: [],
      };
      return newBot;
    }
    const res = await axiosInstance.post("/api/chatbots", { name, description, domain });
    return res.data;
  },

  /** GET /api/chatbots/:id */
  getChatbot: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(250);
      const bot = chatbots.find((c) => c.id === id);
      if (!bot) throw { message: "Chatbot không tồn tại.", status: 404 };
      return { ...bot };
    }
    const res = await axiosInstance.get(`/api/chatbots/${id}`);
    return res.data;
  },

  /** PUT /api/chatbots/:id — payload: { name, description, status, systemPrompt, modelConfig } */
  updateChatbot: async (id, payload) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      const idx = chatbots.findIndex((c) => c.id === id);
      if (idx < 0) throw { message: "Chatbot không tồn tại.", status: 404 };
      Object.assign(chatbots[idx], payload, { updatedAt: new Date().toISOString() });
      return { ...chatbots[idx] };
    }
    const res = await axiosInstance.put(`/api/chatbots/${id}`, payload);
    return res.data;
  },

  /** DELETE /api/chatbots/:id — soft delete */
  deleteChatbot: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(350);
      const idx = chatbots.findIndex((c) => c.id === id);
      if (idx < 0) throw { message: "Chatbot không tồn tại.", status: 404 };
      chatbots[idx].status = "DELETED";
      chatbots[idx].updatedAt = new Date().toISOString();
      return { success: true };
    }
    const res = await axiosInstance.delete(`/api/chatbots/${id}`);
    return res.data;
  },

  /** GET /api/chatbots/:id/embed-config */
  getEmbedConfig: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(250);
      const config = embedConfigs[id];
      if (!config) throw { message: "Embed config không tồn tại.", status: 404 };
      return { ...config };
    }
    const res = await axiosInstance.get(`/api/chatbots/${id}/embed-config`);
    return res.data;
  },

  /** PUT /api/chatbots/:id/embed-config — payload: { widgetColor, welcomeMessage, position, allowedOrigins } */
  updateEmbedConfig: async (id, payload) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      if (!embedConfigs[id]) throw { message: "Embed config không tồn tại.", status: 404 };
      Object.assign(embedConfigs[id], payload);
      return { ...embedConfigs[id] };
    }
    const res = await axiosInstance.put(`/api/chatbots/${id}/embed-config`, payload);
    return res.data;
  },
};
