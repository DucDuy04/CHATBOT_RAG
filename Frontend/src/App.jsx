import { BrowserRouter, Routes, Route, NavLink } from "react-router-dom";
import ChatPage       from "./pages/ChatPage";
import DocumentPage   from "./pages/DocumentPage";
import WidgetChatPage from "./pages/WidgetChatPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>

        {/* Route /widget — KHÔNG có navbar, load thẳng vào iframe */}
        <Route path="/widget" element={<WidgetChatPage />} />

        {/* Tất cả route còn lại — CÓ navbar */}
        <Route path="/*" element={
          <div className="min-h-screen bg-gray-50">

            <nav className="bg-white border-b px-6 py-3 flex gap-6 text-sm">
              <NavLink
                to="/"
                className={({ isActive }) =>
                  isActive
                    ? "font-semibold text-blue-600"
                    : "text-gray-500 hover:text-gray-800"
                }
              >
                Chat
              </NavLink>
              <NavLink
                to="/documents"
                className={({ isActive }) =>
                  isActive
                    ? "font-semibold text-blue-600"
                    : "text-gray-500 hover:text-gray-800"
                }
              >
                Tài liệu
              </NavLink>
            </nav>

            <Routes>
              <Route path="/"          element={<ChatPage />} />
              <Route path="/documents" element={<DocumentPage />} />
            </Routes>

          </div>
        } />

      </Routes>
    </BrowserRouter>
  );
}