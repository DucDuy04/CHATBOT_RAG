import axios from "axios";
import { toastSingleton } from "../components/common/toastSingleton";

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  timeout: 30000,
  headers: {
    "Content-Type": "application/json",
  },
});

// ─── Request interceptor: thêm Bearer token ───────────────────────────────────

axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("auth_token");
    if (token) {
      config.headers["Authorization"] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ─── Response interceptor: map lỗi → message + Toast ─────────────────────────

const STATUS_MESSAGES = {
  400: "Dữ liệu gửi lên không hợp lệ.",
  401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  403: "Bạn không có quyền thực hiện thao tác này.",
  404: "Không tìm thấy dữ liệu.",
  409: "Dữ liệu bị xung đột.",
  422: "Dữ liệu không hợp lệ.",
  500: "Lỗi máy chủ. Vui lòng thử lại sau.",
};

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;

    // Lấy message từ backend nếu có, fallback sang map cố định
    const backendMsg =
      error?.response?.data?.message ||
      error?.response?.data?.error ||
      null;

    const message =
      backendMsg ||
      STATUS_MESSAGES[status] ||
      "Có lỗi xảy ra. Vui lòng thử lại.";

    // Hiển thị Toast error (trừ 401 — trang sẽ tự redirect)
    if (status !== 401) {
      toastSingleton.error(message);
    }

    // 401: logout + redirect, tránh loop khi đang ở /login
    if (status === 401) {
      localStorage.removeItem("auth_token");
      localStorage.removeItem("auth_user");
      toastSingleton.error(STATUS_MESSAGES[401]);
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }

    return Promise.reject(error);
  }
);

export default axiosInstance;
