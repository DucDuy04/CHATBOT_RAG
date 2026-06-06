/**
 * SkeletonLoader — animated placeholder.
 * variant: "card" | "table" | "line"
 * count: số row (dùng cho table)
 */
export default function SkeletonLoader({ variant = "line", count = 5, className = "" }) {
  if (variant === "card") {
    return (
      <div className={`rounded-xl border bg-white p-5 animate-pulse ${className}`}>
        <div className="h-4 bg-gray-200 rounded w-1/3 mb-3" />
        <div className="h-8 bg-gray-200 rounded w-1/2 mb-2" />
        <div className="h-3 bg-gray-100 rounded w-2/3" />
      </div>
    );
  }

  if (variant === "table") {
    return (
      <div className={`animate-pulse space-y-2 ${className}`}>
        {/* Table header */}
        <div className="flex gap-3 px-4 py-2 bg-gray-100 rounded">
          {[40, 30, 20, 10].map((w, i) => (
            <div key={i} className={`h-3 bg-gray-300 rounded`} style={{ width: `${w}%` }} />
          ))}
        </div>
        {/* Rows */}
        {Array.from({ length: count }).map((_, i) => (
          <div key={i} className="flex gap-3 px-4 py-3 bg-white border rounded">
            {[40, 30, 20, 10].map((w, j) => (
              <div key={j} className="h-3 bg-gray-200 rounded" style={{ width: `${w}%` }} />
            ))}
          </div>
        ))}
      </div>
    );
  }

  // Default: line
  return (
    <div className={`animate-pulse space-y-2 ${className}`}>
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="h-4 bg-gray-200 rounded"
          style={{ width: `${70 + (i % 3) * 10}%` }}
        />
      ))}
    </div>
  );
}
