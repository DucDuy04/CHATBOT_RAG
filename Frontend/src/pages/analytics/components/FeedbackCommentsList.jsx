import EmptyState from "../../../components/common/EmptyState";

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function RatingBadge({ rating }) {
  const positive = Number(rating) === 1;
  return (
    <span
      className={[
        "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
        positive
          ? "border-green-200 bg-green-100 text-green-700"
          : "border-red-200 bg-red-100 text-red-700",
      ].join(" ")}
    >
      {positive ? "Positive" : "Negative"}
    </span>
  );
}

export default function FeedbackCommentsList({ items = [] }) {
  if (!items.length) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <EmptyState
          icon="💬"
          title="No feedback comments"
          message="No comment entries match the selected rating filter."
        />
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="mb-4 text-sm font-semibold text-gray-700">Comments</p>
      <div className="space-y-3">
        {items.map((item) => (
          <div key={item.id} className="rounded-lg border border-gray-200 p-3">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <RatingBadge rating={item.rating} />
              {item.chatbotName && <span className="text-xs text-gray-500">{item.chatbotName}</span>}
              {item.sessionId && <span className="text-xs text-gray-400">Session {item.sessionId}</span>}
              <span className="ml-auto text-xs text-gray-400">{formatDate(item.createdAt)}</span>
            </div>
            <p className="text-sm text-gray-700">{item.comment || "-"}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
