import EmptyState from "../../../components/common/EmptyState";
import SkeletonLoader from "../../../components/common/SkeletonLoader";

const clampPercent = (value) => {
  const num = Number(value);
  if (Number.isNaN(num)) return 0;
  return Math.min(100, Math.max(0, num));
};

export default function ChatbotShareBars({ data = [], loading = false, error = null }) {
  if (loading) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Share by chatbot</p>
        <SkeletonLoader variant="line" count={5} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-100 bg-red-50 p-5">
        <p className="text-sm font-semibold text-red-600">Share by chatbot</p>
        <p className="mt-2 text-sm text-red-500">{error}</p>
      </div>
    );
  }

  if (!data.length) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Share by chatbot</p>
        <EmptyState icon="🤖" title="No chatbot share data" message="No chatbot traffic in this period." />
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="mb-4 text-sm font-semibold text-gray-700">Share by chatbot</p>
      <div className="space-y-4">
        {data.map((item) => {
          const pct = clampPercent(item.share);
          return (
            <div key={item.chatbotId || item.chatbotName}>
              <div className="mb-1 flex items-center justify-between gap-2 text-sm">
                <span className="truncate font-medium text-gray-700">{item.chatbotName || "Unknown chatbot"}</span>
                <span className="text-gray-500">
                  {(item.messageCount ?? 0).toLocaleString()} msgs ({pct.toFixed(1)}%)
                </span>
              </div>
              <div className="h-2.5 w-full rounded-full bg-gray-100">
                <div className="h-2.5 rounded-full bg-blue-500" style={{ width: `${pct}%` }} />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
