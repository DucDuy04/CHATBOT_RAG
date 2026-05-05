import SkeletonLoader from "../../../components/common/SkeletonLoader";
import StatusBadge from "../../../components/common/StatusBadge";
import EmptyState from "../../../components/common/EmptyState";

/** Derive StatusBadge variant từ activity event type */
const TYPE_TO_STATUS = {
  chatbot_created:    "synced",
  document_indexed:   "synced",
  chatbot_updated:    "info",
  document_uploaded:  "processing",
  api_key_generated:  "info",
  document_failed:    "failed",
  chatbot_deactivated:"inactive",
  member_invited:     "pending",
};

/** Chuyển "chatbot_created" → "Chatbot Created" */
function formatEventType(type) {
  return (type || "")
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

/** Chuyển ISO string → "May 5, 10:12 AM" */
function formatTime(isoStr) {
  if (!isoStr) return "—";
  const d = new Date(isoStr);
  return d.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * ActivityTable — bảng hiển thị activity events.
 *
 * Props:
 *   data     — array of { id, type, actor, target, createdAt }
 *   loading  — hiện skeleton khi true
 *   error    — string lỗi nếu có
 *   onRetry  — callback khi click Retry
 */
export default function ActivityTable({ data, loading = false, error = null, onRetry }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-gray-100">
        <p className="text-sm font-semibold text-gray-700">Recent Activity</p>
        <p className="text-xs text-gray-400 mt-0.5">Latest system events</p>
      </div>

      {/* Loading */}
      {loading && (
        <div className="p-4">
          <SkeletonLoader variant="table" count={5} />
        </div>
      )}

      {/* Error */}
      {!loading && error && (
        <div className="p-8 text-center">
          <p className="text-sm text-red-500 mb-3">{error}</p>
          {onRetry && (
            <button
              onClick={onRetry}
              className="text-xs text-blue-600 hover:underline"
            >
              Try again
            </button>
          )}
        </div>
      )}

      {/* Empty */}
      {!loading && !error && (!data || data.length === 0) && (
        <EmptyState
          icon="📋"
          title="No activity"
          message="No recent events found."
        />
      )}

      {/* Table — horizontal scroll on small screens */}
      {!loading && !error && data && data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm min-w-[600px]">
            <thead>
              <tr className="bg-gray-50 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="px-5 py-3 text-left">Event</th>
                <th className="px-5 py-3 text-left">Chatbot / Resource</th>
                <th className="px-5 py-3 text-left">User</th>
                <th className="px-5 py-3 text-left">Time</th>
                <th className="px-5 py-3 text-left">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {data.map((event) => {
                const statusKey = TYPE_TO_STATUS[event.type] || "info";
                return (
                  <tr
                    key={event.id}
                    className="hover:bg-gray-50 transition-colors"
                  >
                    <td className="px-5 py-3 font-medium text-gray-800 whitespace-nowrap">
                      {formatEventType(event.type)}
                    </td>
                    <td className="px-5 py-3 text-gray-600 max-w-[180px] truncate">
                      {event.target || "—"}
                    </td>
                    <td className="px-5 py-3 text-gray-500 whitespace-nowrap">
                      {event.actor || "—"}
                    </td>
                    <td className="px-5 py-3 text-gray-400 whitespace-nowrap">
                      {formatTime(event.createdAt)}
                    </td>
                    <td className="px-5 py-3">
                      <StatusBadge status={statusKey} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
