// src/components/layout/MainLayout.jsx
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';

export default function MainLayout() {
  return (
    <div className="flex h-screen w-screen bg-[#f8f9fa] overflow-hidden">
      {/* Cột trái: Sidebar cố định */}
      <Sidebar />
      
      {/* Cột phải: Nội dung thay đổi theo route */}
      <main className="flex-1 h-full overflow-y-auto relative">
        {/* Nơi render các trang Dashboard, KnowledgeBase... */}
        <Outlet />
      </main>
    </div>
  );
}