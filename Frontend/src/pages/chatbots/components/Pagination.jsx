/**
 * Pagination — Previous / current / Next.
 *
 * Props:
 *   page        — current page index (0-based)
 *   totalPages  — total number of pages
 *   total       — total item count
 *   size        — items per page
 *   onPageChange — callback(newPage: number)
 *
 * Ẩn hoàn toàn nếu chỉ có 1 trang và total <= size.
 */
export default function Pagination({ page, totalPages, total, size, onPageChange }) {
  // Không hiển thị nếu không cần thiết
  if (totalPages <= 1 && total <= size) return null;

  const displayPage = page + 1; // Hiển thị 1-based cho user
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min((page + 1) * size, total);

  return (
    <div className="flex items-center justify-between py-1">
      {/* Info */}
      <p className="text-xs text-gray-400">
        {total === 0
          ? "No results"
          : `Showing ${from}–${to} of ${total} chatbots`}
      </p>

      {/* Controls */}
      <div className="flex items-center gap-2">
        <button
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-lg
                     hover:bg-gray-50 transition-colors
                     disabled:opacity-40 disabled:cursor-not-allowed"
          aria-label="Previous page"
        >
          ← Prev
        </button>

        <span className="text-sm text-gray-500 min-w-[60px] text-center tabular-nums">
          {displayPage} / {totalPages}
        </span>

        <button
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-lg
                     hover:bg-gray-50 transition-colors
                     disabled:opacity-40 disabled:cursor-not-allowed"
          aria-label="Next page"
        >
          Next →
        </button>
      </div>
    </div>
  );
}
