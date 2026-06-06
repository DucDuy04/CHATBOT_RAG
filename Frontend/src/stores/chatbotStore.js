import { create } from "zustand";
import { chatbotsApi } from "../api";

/**
 * chatbotStore — quản lý list/filter/pagination/loading cho Chatbot Management.
 *
 * Không persist vào localStorage.
 * Không lẫn với authStore.
 * UI state (modal open/close) giữ ở component.
 */
const useChatbotStore = create((set, get) => ({
  // ─── List data ────────────────────────────────────────────────────────────
  items: [],
  loading: false,
  error: null,

  // ─── Create state ─────────────────────────────────────────────────────────
  creating: false,
  createError: null,

  // ─── Domain list cho filter dropdown ──────────────────────────────────────
  // Populate từ first fetch (khi không có domain filter)
  domains: [],

  // ─── Filters ──────────────────────────────────────────────────────────────
  filters: {
    search: "",
    status: "",
    domain: "",
  },

  // ─── Pagination ───────────────────────────────────────────────────────────
  pagination: {
    page: 0,
    size: 10,
    total: 0,
    totalPages: 1,
  },

  // ─── Filter actions ───────────────────────────────────────────────────────

  setSearch: (search) =>
    set((s) => ({
      filters: { ...s.filters, search },
      pagination: { ...s.pagination, page: 0 },
    })),

  setStatus: (status) =>
    set((s) => ({
      filters: { ...s.filters, status },
      pagination: { ...s.pagination, page: 0 },
    })),

  setDomain: (domain) =>
    set((s) => ({
      filters: { ...s.filters, domain },
      pagination: { ...s.pagination, page: 0 },
    })),

  setPage: (page) =>
    set((s) => ({ pagination: { ...s.pagination, page } })),

  setSize: (size) =>
    set((s) => ({ pagination: { ...s.pagination, size, page: 0 } })),

  resetFilters: () =>
    set((s) => ({
      filters: { search: "", status: "", domain: "" },
      pagination: { ...s.pagination, page: 0 },
    })),

  // ─── Fetch ────────────────────────────────────────────────────────────────

  fetchChatbots: async () => {
    const { filters, pagination } = get();
    set({ loading: true, error: null });
    try {
      const result = await chatbotsApi.getChatbots({
        search: filters.search,
        status: filters.status,
        domain: filters.domain,
        page: pagination.page,
        size: pagination.size,
      });
      set((s) => {
        // Cập nhật domain list nếu đang không filter theo domain
        // (để dropdown luôn có đủ options từ dữ liệu thật)
        const freshDomains = !filters.domain
          ? [...new Set([
              ...s.domains,
              ...result.items.map((c) => c.domain).filter(Boolean),
            ])]
          : s.domains;
        return {
          items: result.items,
          pagination: {
            ...s.pagination,
            total: result.total,
            totalPages: result.totalPages,
          },
          domains: freshDomains,
          loading: false,
        };
      });
    } catch (err) {
      set({ loading: false, error: err?.message || "Failed to load chatbots." });
    }
  },

  // ─── Create ───────────────────────────────────────────────────────────────

  createChatbot: async (payload) => {
    set({ creating: true, createError: null });
    try {
      const newBot = await chatbotsApi.createChatbot(payload);
      set({ creating: false });
      return newBot;
    } catch (err) {
      const msg = err?.message || "Failed to create chatbot.";
      set({ creating: false, createError: msg });
      throw err;
    }
  },
}));

export default useChatbotStore;
