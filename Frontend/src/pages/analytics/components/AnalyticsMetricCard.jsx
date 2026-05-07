import SkeletonLoader from "../../../components/common/SkeletonLoader";

function formatValue(value, suffix = "") {
  if (value == null) return "-";
  return `${Number(value).toLocaleString()}${suffix}`;
}

export default function AnalyticsMetricCard({
  title,
  value,
  delta,
  valueSuffix = "",
  loading = false,
  error = null,
}) {
  if (loading) return <SkeletonLoader variant="card" />;

  if (error) {
    return (
      <div className="rounded-xl border border-red-100 bg-red-50 p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-red-600">{title}</p>
        <p className="mt-2 text-sm text-red-500">{error}</p>
      </div>
    );
  }

  const deltaValue = typeof delta === "number" ? delta : null;
  const deltaClass =
    deltaValue == null ? "text-gray-400" : deltaValue > 0 ? "text-green-600" : deltaValue < 0 ? "text-red-600" : "text-gray-400";
  const deltaText = deltaValue == null ? "-" : `${deltaValue > 0 ? "+" : ""}${deltaValue}%`;

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{title}</p>
      <p className="mt-2 text-3xl font-bold text-gray-900">{formatValue(value, valueSuffix)}</p>
      <p className={`mt-2 text-sm font-semibold ${deltaClass}`}>{deltaText}</p>
    </div>
  );
}
