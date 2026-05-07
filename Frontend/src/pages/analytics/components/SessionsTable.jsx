function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  return date.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function renderRating(rating) {
  if (rating == null) return "-";
  const num = Number(rating);
  if (Number.isNaN(num)) return String(rating);
  const stars = "★".repeat(Math.max(Math.min(Math.round(num), 5), 0));
  return `${num}/5 ${stars}`;
}

export default function SessionsTable({ items = [], onView }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[860px] text-sm">
        <thead>
          <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500">
            <th className="py-2 pr-3">Session ID</th>
            <th className="px-3 py-2">Chatbot</th>
            <th className="px-3 py-2">Messages</th>
            <th className="px-3 py-2">Rating</th>
            <th className="px-3 py-2">Date</th>
            <th className="py-2 pl-3 text-right">View</th>
          </tr>
        </thead>
        <tbody>
          {items.map((session) => (
            <tr key={session.id} className="border-b border-gray-100">
              <td className="py-3 pr-3 font-mono text-xs text-gray-700">{session.id || "-"}</td>
              <td className="px-3 py-3 text-gray-700">{session.chatbotName || "-"}</td>
              <td className="px-3 py-3 text-gray-600">{session.messageCount ?? "-"}</td>
              <td className="px-3 py-3 text-gray-600">{renderRating(session.rating)}</td>
              <td className="px-3 py-3 text-gray-600">{formatDateTime(session.createdAt)}</td>
              <td className="py-3 pl-3 text-right">
                <button
                  type="button"
                  onClick={() => onView(session)}
                  className="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-50"
                >
                  View
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
