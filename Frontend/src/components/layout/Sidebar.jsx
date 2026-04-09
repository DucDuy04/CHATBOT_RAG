// function Sidebar({ items = [], activePath = '', onNavigate }) {
//   return (
//     <aside>
//       <h2>Admin Navigation</h2>
//       <nav>
//         <ul>
//           {items.length === 0 ? (
//             <li>No navigation items yet.</li>
//           ) : (
//             items.map((item) => (
//               <li key={item.path}>
//                 <button
//                   type="button"
//                   onClick={() => onNavigate?.(item.path)}
//                   aria-current={activePath === item.path ? 'page' : undefined}
//                 >
//                   {item.label}
//                 </button>
//               </li>
//             ))
//           )}
//         </ul>
//       </nav>
//     </aside>
//   );
// }

// export default Sidebar;

// src/components/layout/Sidebar.jsx
import { NavLink } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Database, 
  Bot, 
  PlayCircle, 
  Settings 
} from 'lucide-react';

export default function Sidebar() {
  // Tách data menu ra array để dễ quản lý, dễ scale
  const mainMenu =[
    { name: 'Dashboard', path: '/', icon: LayoutDashboard },
    { name: 'Knowledge Base', path: '/knowledge-base', icon: Database },
    { name: 'Chatbots', path: '/chatbots', icon: Bot },
    { name: 'Playground', path: '/playground', icon: PlayCircle },
  ];

  const systemMenu =[
    { name: 'Settings', path: '/settings', icon: Settings },
  ];

  return (
    <aside className="w-64 h-screen bg-white border-r border-gray-100 flex flex-col font-sans flex-shrink-0">
      
      {/* 1. Header & Logo */}
      <div className="h-20 flex items-center px-6 border-b border-transparent">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center text-white shadow-md shadow-blue-200">
            <Bot size={24} strokeWidth={2} />
          </div>
          <div className="flex flex-col">
            <span className="font-bold text-gray-900 text-lg leading-tight">RAG System</span>
            <span className="text-xs text-gray-400 font-medium">Management Portal</span>
          </div>
        </div>
      </div>

      {/* 2. Navigation Menu */}
      <div className="flex-1 overflow-y-auto py-6 px-4 flex flex-col gap-1">
        
        {/* Main Menu */}
        {mainMenu.map((item) => (
          <NavLink
            key={item.name}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200 font-medium ${
                isActive 
                  ? 'bg-blue-50 text-blue-600' 
                  : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'
              }`
            }
          >
            <item.icon size={20} className="flex-shrink-0" />
            <span className="text-sm">{item.name}</span>
          </NavLink>
        ))}

        {/* System Menu Separator */}
        <div className="mt-6 mb-2 px-4">
          <span className="text-[11px] font-bold text-gray-400 tracking-wider uppercase">
            System
          </span>
        </div>

        {systemMenu.map((item) => (
          <NavLink
            key={item.name}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200 font-medium ${
                isActive 
                  ? 'bg-blue-50 text-blue-600' 
                  : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'
              }`
            }
          >
            <item.icon size={20} className="flex-shrink-0" />
            <span className="text-sm">{item.name}</span>
          </NavLink>
        ))}
      </div>

      {/* 3. Footer Widget (Storage Usage) */}
      <div className="p-4 mt-auto">
        <div className="bg-gray-50 p-4 rounded-2xl border border-gray-100">
          <div className="text-xs font-semibold text-gray-600 mb-2">Storage Usage</div>
          <div className="w-full bg-gray-200 rounded-full h-1.5 mb-2">
            <div className="bg-blue-600 h-1.5 rounded-full w-[65%]"></div>
          </div>
          <div className="text-[11px] text-gray-500">
            <span className="font-medium text-gray-700">650MB</span> of 1GB used
          </div>
        </div>
      </div>

    </aside>
  );
}
