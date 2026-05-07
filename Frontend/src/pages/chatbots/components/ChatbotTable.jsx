import { useNavigate } from "react-router-dom";
import SkeletonLoader from "../../../components/common/SkeletonLoader";
import EmptyState from "../../../components/common/EmptyState";
import StatusBadge from "../../../components/common/StatusBadge";

/** Chuyển ISO string → "May 1, 2026" */
function formatDate(isoStr) {
  if (!isoStr) return "—";
  const d = new Date(isoStr);
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/**
 * Avatar — hiển thị initials nếu không có avatarUrl.
 * Màu background dựa trên ký tự đầu của initials.
 */
const AVATAR_COLORS = [
  "bg-blue-100 text-blue-700",
  "bg-emerald-100 text-emerald-700",
  "bg-violet-100 text-violet-700",
  "bg-amber-100 text-amber-700",
  "bg-rose-100 text-rose-700",
  "bg-cyan-100 text-cyan-700",
];

function avatarColorFor(initials) {
  const code = (initials || "?").charCodeAt(0);
  return AVATAR_COLORS[code % AVATAR_COLORS.length];
}

function Avatar({ initials, avatarUrl, name }) {
  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={name}
        className="w-9 h-9 rounded-full object-cover flex-shrink-0"
      />
    );
  }
  const label = initials || (name ? name.slice(0, 2).toUpperCase() : "??");
  return (
    <div
      className={`w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 ${avatarColorFor(label)}`}
    >
      {label}
    </div>
  );
}

/**
 * ChatbotTable — bảng danh sách chatbots.
 *
 * Props:
 *   items    — array chatbot objects
 *   loading  — hiện skeleton khi true
 *   error    — string lỗi nếu có
 *   onRetry  — callback retry
 */
export default function ChatbotTable({ items, loading, error, onRetry }) {
  const navigate = useNavigate();

  if (loading) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-4">
        <SkeletonLoader variant="table" count={5} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center">
        <p className="text-sm font-medium text-red-600 mb-3">{error}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="text-sm text-red-700 font-medium hover:underline"
          >
            Try again
          </button>
        )}
      </div>
    );
  }

  if (!items || items.length === 0) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
        <EmptyState
          icon="🤖"
          title="No chatbots found"
          message="Try adjusting your search or filters, or create a new chatbot."
        />
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      {/* Horizontal scroll on small screens */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm min-w-[720px]">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-100 text-xs font-semibold text-gray-500 uppercase tracking-wider">
              <th className="px-4 py-3 w-12" aria-label="Avatar" />
              <th className="px-4 py-3 text-left">Name</th>
              <th className="px-4 py-3 text-left">Domain</th>
              <th className="px-4 py-3 text-center">Docs</th>
              <th className="px-4 py-3 text-center">Messages</th>
              <th className="px-4 py-3 text-left">Status</th>
              <th className="px-4 py-3 text-left">Updated</th>
              <th className="px-4 py-3 w-16" aria-label="Actions" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {items.map((bot) => (
              <tr
                key={bot.id}
                className="hover:bg-gray-50 transition-colors"
              >
                {/* Avatar */}
                <td className="px-4 py-3">
                  <Avatar
                    initials={bot.initials}
                    avatarUrl={bot.avatarUrl}
                    name={bot.name}
                  />
                </td>

                {/* Name + Description */}
                <td className="px-4 py-3 max-w-[240px]">
                  <p className="font-medium text-gray-800 truncate">{bot.name}</p>
                  {bot.description && (
                    <p className="text-xs text-gray-400 truncate mt-0.5">
                      {bot.description}
                    </p>
                  )}
                </td>

                {/* Domain */}
                <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                  {bot.domain || "—"}
                </td>

                {/* Docs */}
                <td className="px-4 py-3 text-center text-gray-700 tabular-nums">
                  {bot.documentCount ?? "—"}
                </td>

                {/* Messages */}
                <td className="px-4 py-3 text-center text-gray-700 tabular-nums whitespace-nowrap">
                  {bot.messageCount != null
                    ? bot.messageCount.toLocaleString()
                    : "—"}
                </td>

                {/* Status */}
                <td className="px-4 py-3">
                  <StatusBadge
                    status={
                      bot.status?.toUpperCase() === "ACTIVE" ? "active" : "inactive"
                    }
                  />
                </td>

                {/* Updated */}
                <td className="px-4 py-3 text-gray-400 text-xs whitespace-nowrap">
                  {formatDate(bot.updatedAt)}
                </td>

                {/* Config button */}
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={() => navigate(`/chatbots/${bot.id}/config`)}
                    className="px-2.5 py-1.5 text-xs font-medium text-gray-600 bg-gray-100
                               rounded-lg hover:bg-gray-200 transition-colors whitespace-nowrap"
                    title={`Configure ${bot.name}`}
                  >
                    Config
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
