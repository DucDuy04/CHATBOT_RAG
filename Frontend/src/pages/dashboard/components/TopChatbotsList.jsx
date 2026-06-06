import SkeletonLoader from "../../../components/common/SkeletonLoader";
import EmptyState from "../../../components/common/EmptyState";

/**
 * TopChatbotsList — hiển thị top N chatbots theo messageCount.
 *
 * Props:
 *   data     — array of { id, name, messageCount, domain? }
 *   loading  — hiện skeleton khi true
 *   error    — string lỗi nếu có
 *   onRetry  — callback khi click Retry
 */
export default function TopChatbotsList({ data, loading = false, error = null, onRetry }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-gray-100">
        <p className="text-sm font-semibold text-gray-700">Top Chatbots</p>
        <p className="text-xs text-gray-400 mt-0.5">By message count</p>
      </div>

      {/* Body */}
      <div className="p-4">
        {loading && <SkeletonLoader variant="line" count={5} />}

        {!loading && error && (
          <div className="py-4 text-center">
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

        {!loading && !error && (!data || data.length === 0) && (
          <EmptyState
            icon="🤖"
            title="No chatbots"
            message="No chatbot data available yet."
          />
        )}

        {!loading && !error && data && data.length > 0 && (
          <ul className="space-y-0 divide-y divide-gray-50">
            {data.slice(0, 5).map((bot, idx) => (
              <li key={bot.id} className="flex items-center gap-3 py-3">
                {/* Rank */}
                <span
                  className={`
                    flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center
                    text-xs font-bold
                    ${idx === 0 ? "bg-amber-100 text-amber-700" : ""}
                    ${idx === 1 ? "bg-gray-100 text-gray-600" : ""}
                    ${idx === 2 ? "bg-orange-50 text-orange-600" : ""}
                    ${idx > 2 ? "bg-gray-50 text-gray-400" : ""}
                  `}
                >
                  {idx + 1}
                </span>

                {/* Name + domain */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-800 truncate">
                    {bot.name}
                  </p>
                  {bot.domain && (
                    <p className="text-xs text-gray-400 truncate">{bot.domain}</p>
                  )}
                </div>

                {/* Stats */}
                <div className="flex-shrink-0 text-right">
                  <p className="text-sm font-semibold text-gray-700">
                    {bot.messageCount != null
                      ? bot.messageCount.toLocaleString()
                      : "—"}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
