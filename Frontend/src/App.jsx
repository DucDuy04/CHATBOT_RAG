// import { BrowserRouter, Routes, Route, NavLink } from "react-router-dom";
// import ChatPage       from "./pages/ChatPage";
// import DocumentPage   from "./pages/DocumentPage";
// import WidgetChatPage from "./pages/WidgetChatPage";

// export default function App() {
//   return (
//     <BrowserRouter>
//       <Routes>

//         {/* Route /widget — KHÔNG có navbar, load thẳng vào iframe */}
//         <Route path="/widget" element={<WidgetChatPage />} />

//         {/* Tất cả route còn lại — CÓ navbar */}
//         <Route path="/*" element={
//           <div className="min-h-screen bg-gray-50">

//             <nav className="bg-white border-b px-6 py-3 flex gap-6 text-sm">
//               <NavLink
//                 to="/"
//                 className={({ isActive }) =>
//                   isActive
//                     ? "font-semibold text-blue-600"
//                     : "text-gray-500 hover:text-gray-800"
//                 }
//               >
//                 Chat
//               </NavLink>
//               <NavLink
//                 to="/documents"
//                 className={({ isActive }) =>
//                   isActive
//                     ? "font-semibold text-blue-600"
//                     : "text-gray-500 hover:text-gray-800"
//                 }
//               >
//                 Tài liệu
//               </NavLink>
//             </nav>

//             <Routes>
//               <Route path="/"          element={<ChatPage />} />
//               <Route path="/documents" element={<DocumentPage />} />
//             </Routes>

//           </div>
//         } />

//       </Routes>
//     </BrowserRouter>
//   );
// }

// src/App.jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import MainLayout from './components/layout/MainLayout';

// Import Pages
import DashboardPage from './pages/DashboardPage';
import DocumentPage from './pages/DocumentPage';
// import ChatPage from './pages/ChatPage';
import ChatPage from './pages/Chatbots';
import Playground from './pages/Playground';
import SettingsPage from './pages/SettingsPage';
import Chatbots from './pages/Chatbots';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Mọi Route nằm trong này đều có Sidebar do bị bọc bởi MainLayout */}
        <Route element={<MainLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/knowledge-base" element={<DocumentPage />} />
          <Route path="/chatbots" element={<Chatbots />} />
          <Route path="/playground" element={<Playground />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;