import { NavLink } from "react-router-dom";

const NAV_ITEMS = [
  { to: "/dashboard",  label: "Dashboard",  icon: "⊞" },
  { to: "/chatbots",   label: "Chatbots",   icon: "◈" },
  { to: "/documents",  label: "Documents",  icon: "⊟" },
  { to: "/playground", label: "Playground", icon: "▷" },
  { to: "/analytics",  label: "Analytics",  icon: "◉" },
  { to: "/settings",   label: "Settings",   icon: "⚙" },
];

export default function Sidebar({ collapsed, onClose }) {
  return (
    <>
      {/* Overlay cho mobile */}
      {onClose && !collapsed && (
        <div
          className="fixed inset-0 z-20 bg-black/40 lg:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={`
          fixed top-0 left-0 z-30 h-full bg-gray-900 text-white flex flex-col
          transition-all duration-200 ease-in-out
          ${collapsed ? "w-14" : "w-[188px]"}
        `}
      >
        {/* Logo / Brand */}
        <div className="flex items-center gap-3 px-4 h-12 border-b border-gray-700 shrink-0">
          <span className="text-blue-400 text-lg font-bold leading-none">◈</span>
          {!collapsed && (
            <span className="text-sm font-semibold text-white truncate">
              RAG Chatbot
            </span>
          )}
        </div>

        {/* Nav items */}
        <nav className="flex-1 py-3 overflow-y-auto">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={onClose}
              className={({ isActive }) =>
                `flex items-center gap-3 px-4 py-2.5 text-sm transition-colors
                 ${isActive
                   ? "bg-blue-600 text-white font-medium"
                   : "text-gray-400 hover:bg-gray-800 hover:text-white"
                 }
                 ${collapsed ? "justify-center" : ""}`
              }
              title={collapsed ? item.label : undefined}
            >
              <span className="text-base shrink-0">{item.icon}</span>
              {!collapsed && <span className="truncate">{item.label}</span>}
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        {!collapsed && (
          <div className="px-4 py-3 border-t border-gray-700 shrink-0">
            <p className="text-[11px] text-gray-500 truncate">v0.1.0 · local</p>
          </div>
        )}
      </aside>
    </>
  );
}
