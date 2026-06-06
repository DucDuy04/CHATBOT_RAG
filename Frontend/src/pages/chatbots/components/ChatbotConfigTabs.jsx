import { NavLink } from "react-router-dom";

/**
 * ChatbotConfigTabs — tab bar Config | Embed cho chatbot detail pages.
 *
 * Config tab: active khi path endsWith /config.
 * Embed tab: link tới /chatbots/:id/embed.
 * Dùng NavLink — không reload full page.
 */
export default function ChatbotConfigTabs({ id }) {
  const baseClass =
    "px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap";
  const activeClass = "border-blue-600 text-blue-600";
  const inactiveClass =
    "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300";

  return (
    <div className="flex border-b border-gray-200 mb-6">
      <NavLink
        to={`/chatbots/${id}/config`}
        end
        className={({ isActive }) =>
          `${baseClass} ${isActive ? activeClass : inactiveClass}`
        }
      >
        Config
      </NavLink>
      <NavLink
        to={`/chatbots/${id}/embed`}
        className={({ isActive }) =>
          `${baseClass} ${isActive ? activeClass : inactiveClass}`
        }
      >
        Embed
      </NavLink>
    </div>
  );
}
