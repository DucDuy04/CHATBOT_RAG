import axiosInstance from "./axiosInstance";
import { USE_MOCK_API, mockDelay } from "./apiMode";
import {
  dashboardSummary,
  messageVolume7d,
  topChatbots,
  activityEvents,
} from "../mocks/dashboardMock";

export const dashboardApi = {
  /** GET /api/dashboard/summary */
  getSummary: async () => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      return { ...dashboardSummary };
    }
    const res = await axiosInstance.get("/api/dashboard/summary");
    return res.data;
  },

  /** GET /api/dashboard/message-volume?days=7 */
  getMessageVolume: async (days = 7) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return messageVolume7d.slice(-days);
    }
    const res = await axiosInstance.get("/api/dashboard/message-volume", { params: { days } });
    return res.data;
  },

  /** GET /api/dashboard/top-chatbots?limit=5 */
  getTopChatbots: async (limit = 5) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return topChatbots.slice(0, limit);
    }
    const res = await axiosInstance.get("/api/dashboard/top-chatbots", { params: { limit } });
    return res.data;
  },

  /** GET /api/dashboard/activity?limit=20 */
  getActivity: async (limit = 20) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return activityEvents.slice(0, limit);
    }
    const res = await axiosInstance.get("/api/dashboard/activity", { params: { limit } });
    return res.data;
  },
};
