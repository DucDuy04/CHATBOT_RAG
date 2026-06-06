/**
 * publicChatApi — dùng fetch thay vì axiosInstance để không tự gắn JWT Bearer.
 * Header x-api-key được set tường minh.
 */
const API_BASE = (import.meta.env.VITE_API_URL || "").trim();

export const publicChatApi = {
  /**
   * POST /api/public/chat
   * Header: x-api-key (không có Authorization JWT)
   */
  publicChat: async ({ apiKey, message, sessionId }) => {
    if (!apiKey) {
      throw new Error("Thiếu API key. Vui lòng cung cấp x-api-key hợp lệ.");
    }

    const response = await fetch(`${API_BASE}/api/public/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-api-key": apiKey,
      },
      body: JSON.stringify({ message, sessionId }),
    });

    if (!response.ok) {
      let errMsg = `HTTP ${response.status}`;
      try {
        const data = await response.json();
        errMsg = data.message || data.error || errMsg;
      } catch { /* ignore parse error */ }
      const err = new Error(errMsg);
      err.status = response.status;
      throw err;
    }

    return response.json();
  },
};
