import SkeletonLoader from "../../../components/common/SkeletonLoader";
import EmptyState from "../../../components/common/EmptyState";

/**
 * SessionList — left panel showing past playground sessions.
 * Props:
 *   sessions         — array of session summary objects
 *   selectedSessionId — currently active session id or null
 *   onSelect         — (session) => void
 *   onDelete         — (sessionId: string) => void
 *   loading          — bool
 *   chatbotSelected  — bool, whether a chatbot has been chosen
 */
export default function SessionList({
  sessions,
  selectedSessionId,
  onSelect,
  onDelete,
  loading,
  chatbotSelected,
}) {
  const fmtDate = (iso) => {
    try {
      const d = new Date(iso);
      return (
        d.toLocaleDateString("vi-VN", { month: "short", day: "numeric" }) +
        " " +
        d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })
      );
    } catch {
      return iso;
    }
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-3 py-2.5 border-b shrink-0">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
          Sessions
        </p>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto">
        {!chatbotSelected ? (
          <EmptyState
            icon="🤖"
            title="Chưa chọn chatbot"
            message="Chọn chatbot để xem sessions."
            className="py-8 px-2"
          />
        ) : loading ? (
          <div className="p-3">
            <SkeletonLoader variant="line" count={4} />
          </div>
        ) : sessions.length === 0 ? (
          <EmptyState
            icon="📋"
            title="Chưa có session"
            message="Gửi tin nhắn để tạo session mới."
            className="py-8 px-2"
          />
        ) : (
          <ul className="divide-y divide-gray-100">
            {sessions.map((sess) => (
              <li key={sess.id}>
                <button
                  onClick={() => onSelect(sess)}
                  className={`w-full text-left px-3 py-2.5 hover:bg-gray-50 group transition-colors ${
                    selectedSessionId === sess.id ? "bg-blue-50" : ""
                  }`}
                >
                  <div className="flex items-start justify-between gap-1">
                    <p
                      className={`text-xs font-medium truncate flex-1 leading-snug ${
                        selectedSessionId === sess.id
                          ? "text-blue-700"
                          : "text-gray-700"
                      }`}
                    >
                      {sess.lastMessage || `Session ${sess.id.slice(-4)}`}
                    </p>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(sess.id);
                      }}
                      className="shrink-0 opacity-0 group-hover:opacity-100 text-gray-400 hover:text-red-500 transition-opacity text-xs leading-none p-0.5 -mt-0.5"
                      title="Xóa session"
                    >
                      ✕
                    </button>
                  </div>
                  <p className="text-xs text-gray-400 mt-0.5">{fmtDate(sess.updatedAt)}</p>
                  {sess.messageCount != null && (
                    <p className="text-xs text-gray-400">{sess.messageCount} tin nhắn</p>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
