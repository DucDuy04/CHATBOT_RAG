import { useState } from "react";
import { useNavigate } from "react-router-dom";
import useAuthStore from "../stores/authStore";

/**
 * LoginPage — placeholder với Mock Login button cho dev.
 * Production auth chưa implement ở prompt này.
 */
export default function LoginPage() {
  const login    = useAuthStore((s) => s.login);
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const handleMockLogin = () => {
    setLoading(true);
    // Mock: set token + user giả để bypass PrivateRoute trong dev
    setTimeout(() => {
      login("mock-token-dev-only", { name: "Dev User", email: "dev@local" });
      navigate("/dashboard");
      setLoading(false);
    }, 400);
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-sm border p-8 w-full max-w-sm">

        {/* Logo */}
        <div className="flex items-center gap-2 mb-8">
          <span className="text-blue-600 text-2xl">◈</span>
          <span className="font-bold text-gray-800 text-lg">RAG Chatbot</span>
        </div>

        <h1 className="text-xl font-semibold text-gray-800 mb-1">Đăng nhập</h1>
        <p className="text-sm text-gray-500 mb-8">
          Truy cập bảng quản trị chatbot của bạn.
        </p>

        {/* Placeholder: production auth inputs sẽ thêm sau */}
        <div className="space-y-3 mb-6">
          <input
            type="email"
            disabled
            placeholder="Email (chưa implement)"
            className="w-full px-3 py-2.5 text-sm border rounded-lg bg-gray-50 text-gray-400 cursor-not-allowed"
          />
          <input
            type="password"
            disabled
            placeholder="Mật khẩu (chưa implement)"
            className="w-full px-3 py-2.5 text-sm border rounded-lg bg-gray-50 text-gray-400 cursor-not-allowed"
          />
        </div>

        <button
          disabled
          className="w-full py-2.5 text-sm font-medium bg-blue-600 text-white rounded-lg
                     opacity-40 cursor-not-allowed mb-3"
        >
          Đăng nhập
        </button>

        {/* Mock login — chỉ dùng trong dev */}
        <div className="relative mb-3">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-gray-200" />
          </div>
          <div className="relative flex justify-center text-xs">
            <span className="px-2 bg-white text-gray-400">hoặc</span>
          </div>
        </div>

        <button
          onClick={handleMockLogin}
          disabled={loading}
          className="w-full py-2.5 text-sm font-medium border border-blue-300 text-blue-600
                     rounded-lg hover:bg-blue-50 disabled:opacity-50 disabled:cursor-not-allowed
                     flex items-center justify-center gap-2"
        >
          {loading && (
            <span className="w-3 h-3 border-2 border-blue-600 border-t-transparent rounded-full animate-spin" />
          )}
          🔧 Mock Login (dev only)
        </button>

        <p className="text-xs text-gray-400 text-center mt-4">
          Mock login chỉ set token tạm trong localStorage.
        </p>
      </div>
    </div>
  );
}
