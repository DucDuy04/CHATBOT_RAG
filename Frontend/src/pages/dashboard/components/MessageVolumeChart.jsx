import EmptyState from "../../../components/common/EmptyState";

/**
 * Chuyển "2026-04-29" → "Apr 29" dùng UTC để tránh timezone shift.
 */
function formatDate(dateStr) {
  const d = new Date(dateStr + "T00:00:00Z");
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/**
 * ChartSkeleton — animated placeholder cho chart khi đang load.
 */
function ChartSkeleton() {
  const bars = [60, 80, 95, 70, 100, 85, 65];
  return (
    <div className="animate-pulse">
      <div className="h-4 bg-gray-200 rounded w-48 mb-4" />
      <div className="flex items-end gap-2 h-40 px-2">
        {bars.map((h, i) => (
          <div key={i} className="flex flex-col items-center flex-1 gap-1">
            <div
              className="w-full bg-gray-200 rounded-t"
              style={{ height: `${h}%` }}
            />
            <div className="h-3 bg-gray-100 rounded w-full" />
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * MessageVolumeChart — Pure CSS bar chart cho message volume 7 ngày.
 *
 * Props:
 *   data    — array of { date: "YYYY-MM-DD", count: number }
 *   loading — hiện skeleton khi true
 *   error   — string lỗi nếu có
 */
export default function MessageVolumeChart({ data, loading = false, error = null }) {
  if (loading) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <ChartSkeleton />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-100 bg-red-50 p-5">
        <p className="text-sm font-semibold text-red-600 mb-1">
          Message Volume — Last 7 Days
        </p>
        <p className="text-sm text-red-500">{error}</p>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="text-sm font-semibold text-gray-700 mb-4">
          Message Volume — Last 7 Days
        </p>
        <EmptyState
          icon="📊"
          title="No data"
          message="No message volume data available."
        />
      </div>
    );
  }

  const maxCount = Math.max(...data.map((d) => d.count), 1);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="text-sm font-semibold text-gray-700 mb-5">
        Message Volume — Last 7 Days
      </p>

      {/* Chart area: fixed height, bars grow upward */}
      <div className="flex items-end gap-2 h-40" role="img" aria-label="Message volume bar chart">
        {data.map((item) => {
          const heightPct = (item.count / maxCount) * 100;
          return (
            <div
              key={item.date}
              className="flex flex-col items-center flex-1 min-w-0 gap-1"
              title={`${formatDate(item.date)}: ${item.count} messages`}
            >
              {/* Count label on top of bar */}
              <span className="text-xs text-gray-500 leading-none truncate">
                {item.count}
              </span>
              {/* Bar */}
              <div
                className="w-full bg-blue-500 rounded-t hover:bg-blue-600 transition-colors cursor-default"
                style={{ height: `${heightPct}%`, minHeight: "4px" }}
              />
              {/* Date label */}
              <span className="text-xs text-gray-400 leading-none truncate w-full text-center">
                {formatDate(item.date)}
              </span>
            </div>
          );
        })}
      </div>

      {/* Total summary */}
      <div className="mt-3 pt-3 border-t border-gray-100 flex justify-between text-xs text-gray-400">
        <span>Total</span>
        <span className="font-semibold text-gray-600">
          {data.reduce((s, d) => s + d.count, 0).toLocaleString()} messages
        </span>
      </div>
    </div>
  );
}
