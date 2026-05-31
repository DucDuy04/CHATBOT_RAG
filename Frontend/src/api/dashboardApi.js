import axiosInstance from "./axiosInstance";

export const dashboardApi = {
  /** GET /api/dashboard/summary */
  getSummary: async () => {
    const res = await axiosInstance.get("/api/dashboard/summary");
    return res.data;
  },

  /** GET /api/dashboard/message-volume?days=7 */
  getMessageVolume: async (days = 7) => {
    const res = await axiosInstance.get("/api/dashboard/message-volume", { params: { days } });
    return res.data;
  },

  /** GET /api/dashboard/top-chatbots?limit=5 */
  getTopChatbots: async (limit = 5) => {
    const res = await axiosInstance.get("/api/dashboard/top-chatbots", { params: { limit } });
    return res.data;
  },

  /** GET /api/dashboard/activity?limit=20 */
  getActivity: async (limit = 20) => {
    const res = await axiosInstance.get("/api/dashboard/activity", { params: { limit } });
    return res.data;
  },
};
