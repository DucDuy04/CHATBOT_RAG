import EmptyState from "../../../components/common/EmptyState";
import SkeletonLoader from "../../../components/common/SkeletonLoader";

function formatDayLabel(dateStr) {
  const date = new Date(`${dateStr}T00:00:00Z`);
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" });
}

export default function DailyBarChart({ data = [], loading = false, error = null }) {
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

  if (!data.length) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Messages per day</p>
        <EmptyState icon="📊" title="No daily data" message="No messages found in the selected date range." />
      </div>
    );
  }

  const messageValues = data.map((item) => Number(item.messages ?? 0));
  const max = Math.max(...messageValues, 1);
  const min = Math.min(...messageValues, 0);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <p className="text-sm font-semibold text-gray-700">Messages per day</p>
        <p className="text-xs text-gray-500">
          Min: <span className="font-medium">{min}</span> - Max: <span className="font-medium">{max}</span>
        </p>
      </div>

      <div className="flex h-52 items-end gap-2 border-b border-l border-gray-200 px-2 pb-2">
        {data.map((item) => {
          const value = Number(item.messages ?? 0);
          const heightPct = Math.max((value / max) * 100, 2);
          return (
            <div key={item.date} className="flex flex-1 flex-col items-center justify-end gap-2">
              <div
                className="w-full rounded-t bg-blue-500"
                style={{ height: `${heightPct}%` }}
                title={`${formatDayLabel(item.date)}: ${value} messages`}
              />
              <span className="w-full truncate text-center text-[11px] text-gray-500">{formatDayLabel(item.date)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
