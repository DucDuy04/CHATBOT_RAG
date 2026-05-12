import EmptyState from "../../../components/common/EmptyState";

/** Normalize API rows: backend uses { date, count }; tolerate aliases / string counts. */
function normalizeVolumeData(raw) {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((row) => {
      if (!row || typeof row !== "object") return null;
      const date = row.date ?? row.day ?? row.label;
      if (date == null || String(date).trim() === "") return null;
      const n = Number(row.count ?? row.messageCount ?? row.value ?? 0);
      const count = Number.isFinite(n) ? Math.max(0, n) : 0;
      return { date: String(date).trim(), count };
    })
    .filter(Boolean);
}

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

  const normalized = normalizeVolumeData(data);

  if (normalized.length === 0) {
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

  const maxCount = Math.max(...normalized.map((d) => d.count), 0);
  const scaleMax = maxCount > 0 ? maxCount : 1;

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="text-sm font-semibold text-gray-700 mb-5">
        Message Volume — Last 7 Days
      </p>

      {/* Chart: each column is full height so bar % resolves against a defined track */}
      <div className="flex gap-2 h-44" role="img" aria-label="Message volume bar chart">
        {normalized.map((item) => {
          const heightPct = scaleMax > 0 ? (item.count / scaleMax) * 100 : 0;
          return (
            <div
              key={item.date}
              className="flex flex-col flex-1 min-w-0 h-full"
              title={`${formatDate(item.date)}: ${item.count} messages`}
            >
              <span className="text-xs text-gray-500 text-center shrink-0 leading-none mb-1">
                {item.count}
              </span>
              <div className="flex-1 flex flex-col justify-end min-h-0">
                <div
                  className="w-full bg-blue-500 rounded-t hover:bg-blue-600 transition-colors cursor-default shrink-0"
                  style={{
                    height: `${heightPct}%`,
                    minHeight: item.count > 0 && heightPct > 0 ? "4px" : "0",
                  }}
                />
              </div>
              <span className="text-xs text-gray-400 text-center shrink-0 leading-none mt-1 truncate w-full">
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
          {normalized.reduce((s, d) => s + d.count, 0).toLocaleString()} messages
        </span>
      </div>
    </div>
  );
}
