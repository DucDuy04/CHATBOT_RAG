import { useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";

// Layout & Auth
import AppLayout        from "./components/layout/AppLayout";
import PrivateRoute     from "./routes/PrivateRoute";
import { LayoutProvider, useLayout } from "./contexts/LayoutContext";
import { ToastProvider, ToastRegister } from "./components/common/index";
import { ErrorBoundary } from "./components/common/index";

// Public pages
import LoginPage       from "./pages/LoginPage";
import WidgetChatPage  from "./pages/WidgetChatPage";   // giữ nguyên, không sửa

// Placeholder pages — private
import DashboardPage      from "./pages/dashboard/DashboardPage";
import ChatbotsPage       from "./pages/chatbots/ChatbotsPage";
import ChatbotConfigPage  from "./pages/chatbots/ChatbotConfigPage";
import ChatbotEmbedPage   from "./pages/chatbots/ChatbotEmbedPage";
import DocumentsPage      from "./pages/documents/DocumentsPage";
import PlaygroundPage     from "./pages/playground/PlaygroundPage";
import AnalyticsPage      from "./pages/analytics/AnalyticsPage";
import SettingsPage       from "./pages/settings/SettingsPage";

// Legacy prototype pages — giữ lại để tham khảo logic
// Không remove để không làm mất tham chiếu trong giai đoạn dev
// import ChatPage       from "./pages/ChatPage";
// import DocumentPage   from "./pages/DocumentPage";

// PAGE_TITLE map theo route (exact match)
const PAGE_TITLES = {
  "/dashboard":  "Dashboard",
  "/chatbots":   "Chatbots",
  "/documents":  "Documents",
  "/playground": "Playground",
  "/analytics":  "Analytics",
  "/settings":   "Settings",
};

/**
 * AppLayoutWithTitle — wrapper lấy pageTitle + rightSlot theo route và LayoutContext.
 *
 * Thứ tự ưu tiên title:
 *   1. contextTitle (page con gọi setPageTitle("..."))
 *   2. defaultTitle từ PAGE_TITLES route map
 *
 * rightSlot lấy từ context — page con gọi setRightSlot(<node>).
 * Khi pathname thay đổi: resetLayout() xóa contextTitle và rightSlot.
 *
 * Phải render bên trong <LayoutProvider> và bên trong <BrowserRouter>.
 */
function AppLayoutWithTitle() {
  const { pathname } = useLocation();
  const { pageTitle: contextTitle, rightSlot, resetLayout } = useLayout();

  // Reset context khi route thay đổi — tránh button/title của page cũ bị sót
  useEffect(() => {
    resetLayout();
  }, [pathname, resetLayout]);

  // Default title từ route map (exact match trước, rồi prefix match)
  let defaultTitle = PAGE_TITLES[pathname];
  if (!defaultTitle) {
    if (pathname.endsWith("/config"))          defaultTitle = "Chatbot Config";
    else if (pathname.endsWith("/embed"))      defaultTitle = "Chatbot Embed";
    else if (pathname.startsWith("/chatbots")) defaultTitle = "Chatbots";
  }

  // contextTitle override; nếu không có thì dùng defaultTitle
  const effectiveTitle = contextTitle || defaultTitle;

  return <AppLayout pageTitle={effectiveTitle} rightSlot={rightSlot} />;
}

/**
 * PrivateLayoutShell — bọc LayoutProvider quanh AppLayoutWithTitle
 * để các page con bên trong Outlet có thể gọi useLayout().
 */
function PrivateLayoutShell() {
  return (
    <LayoutProvider>
      <AppLayoutWithTitle />
    </LayoutProvider>
  );
}

export default function App() {
  return (
    <ToastProvider>
      {/* ToastRegister expose toast fn ra ngoài React tree (axiosInstance dùng) */}
      <ToastRegister />

      <BrowserRouter>
        <ErrorBoundary>
          <Routes>
            {/* ── PUBLIC ─────────────────────────────────────────────────── */}
            <Route path="/login"  element={<LoginPage />} />
            {/* /widget: NO auth guard, NO AppLayout */}
            <Route path="/widget" element={<WidgetChatPage />} />

            {/* ── PRIVATE ────────────────────────────────────────────────── */}
            <Route element={<PrivateRoute />}>
              <Route element={<PrivateLayoutShell />}>

                {/* Root redirect */}
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="/" element={<Navigate to="/dashboard" replace />} />

                <Route path="/dashboard"  element={<DashboardPage />} />
                <Route path="/chatbots"   element={<ChatbotsPage />} />
                <Route path="/chatbots/:id/config" element={<ChatbotConfigPage />} />
                <Route path="/chatbots/:id/embed"  element={<ChatbotEmbedPage />} />
                <Route path="/documents"  element={<DocumentsPage />} />
                <Route path="/playground" element={<PlaygroundPage />} />
                <Route path="/analytics"  element={<AnalyticsPage />} />
                <Route path="/settings"   element={<SettingsPage />} />

              </Route>
            </Route>

            {/* Catch-all → login */}
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </ErrorBoundary>
      </BrowserRouter>
    </ToastProvider>
  );
}
