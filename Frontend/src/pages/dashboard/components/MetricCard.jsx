import SkeletonLoader from "../../../components/common/SkeletonLoader";

const defaultFormatter = (v) =>
  v != null ? Number(v).toLocaleString() : "—";

/**
 * MetricCard — hiển thị một metric với value, delta % và description.
 *
 * Props:
 *   title       — label của metric
 *   value       — giá trị hiển thị
 *   delta       — số delta (e.g. +2.1 hoặc -3). null = không hiện.
 *   description — text mô tả thêm (e.g. "vs last 7 days")
 *   loading     — hiện skeleton khi true
 *   formatter   — optional function(value) => string
 */
export default function MetricCard({
  title,
  value,
  delta,
  description,
  loading = false,
  formatter = defaultFormatter,
}) {
  if (loading) {
    return <SkeletonLoader variant="card" />;
  }

  const isPositive = delta != null && delta > 0;
  const isNegative = delta != null && delta < 0;

  let deltaClass = "text-gray-400";
  let deltaPrefix = "";
  if (isPositive) {
    deltaClass = "text-green-600";
    deltaPrefix = "+";
  } else if (isNegative) {
    deltaClass = "text-red-600";
  }

  const deltaText = delta != null ? `${deltaPrefix}${delta}%` : null;

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm hover:shadow-md transition-shadow">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
        {title}
      </p>
      <p className="text-3xl font-bold text-gray-900 mb-2 leading-none">
        {formatter(value)}
      </p>
      <div className="flex items-center gap-1.5 min-h-[1.25rem]">
        {deltaText && (
          <span className={`text-sm font-semibold ${deltaClass}`}>
            {deltaText}
          </span>
        )}
        {description && (
          <span className="text-xs text-gray-400">{description}</span>
        )}
      </div>
    </div>
  );
}
