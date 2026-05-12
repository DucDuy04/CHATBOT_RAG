import EmptyState from "../../../components/common/EmptyState";
import SkeletonLoader from "../../../components/common/SkeletonLoader";

const CHART_HEIGHT_PX = 200;

/** Normalize one row from GET /api/analytics/daily (handles alternate field names). */
function normalizeDailyChartRow(item) {
  if (!item || typeof item !== "object") return null;
  const date =
    item.date ??
    item.day ??
    item.label ??
    item.dateStr ??
    "";
  const messages = Number(
    item.messages ?? item.messageCount ?? item.message_count ?? item.count ?? item.value ?? 0
  );
  const sessions = Number(item.sessions ?? item.sessionCount ?? item.session_count ?? 0);
  if (!date) return null;
  return { date, messages: Number.isFinite(messages) ? messages : 0, sessions: Number.isFinite(sessions) ? sessions : 0 };
}

function formatDayLabel(dateStr) {
  if (!dateStr || typeof dateStr !== "string") return "?";
  const safe = /^\d{4}-\d{2}-\d{2}$/.test(dateStr) ? `${dateStr}T00:00:00Z` : dateStr;
  const date = new Date(safe);
  if (Number.isNaN(date.getTime())) return String(dateStr);
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" });
}

export default function DailyBarChart({ data = [], loading = false, error = null }) {
  const normalized = (Array.isArray(data) ? data : [])
    .map(normalizeDailyChartRow)
    .filter(Boolean)
    .sort((a, b) => String(a.date).localeCompare(String(b.date)));

  if (loading) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Messages per day</p>
        <SkeletonLoader variant="line" count={8} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-100 bg-red-50 p-5">
        <p className="text-sm font-semibold text-red-600">Messages per day</p>
        <p className="mt-2 text-sm text-red-500">{error}</p>
      </div>
    );
  }

  if (!normalized.length) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Messages per day</p>
        <EmptyState icon="📊" title="No daily data" message="No messages found in the selected date range." />
      </div>
    );
  }

  const messageValues = normalized.map((item) => item.messages);
  const max = Math.max(...messageValues, 1);
  const min = Math.min(...messageValues, 0);
  const allZero = messageValues.every((v) => v === 0);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <p className="text-sm font-semibold text-gray-700">Messages per day</p>
        <p className="text-xs text-gray-500">
          Min: <span className="font-medium">{min}</span> — Max: <span className="font-medium">{max}</span>
        </p>
      </div>

      {allZero && (
        <p className="mb-2 text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded px-2 py-1">
          All days are zero in this range — bars are shown at minimum height for reference.
        </p>
      )}

      <div
        className="flex items-end gap-2 border-b border-l border-gray-200 px-2 pb-2"
        style={{ height: CHART_HEIGHT_PX }}
      >
        {normalized.map((item) => {
          const value = item.messages;
          const ratio = max > 0 ? value / max : 0;
          const barPx = Math.max(Math.round(ratio * (CHART_HEIGHT_PX - 28)), value > 0 ? 6 : 3);
          return (
            <div key={item.date} className="flex min-w-0 flex-1 flex-col items-center justify-end gap-2">
              <div
                className="w-full max-w-[48px] mx-auto rounded-t bg-blue-500 transition-[height] duration-150"
                style={{ height: barPx }}
                title={`${item.date}: ${value} messages`}
              />
              <span className="w-full truncate text-center text-[11px] text-gray-500">
                {formatDayLabel(item.date)}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
