/**
 * DocumentsPagination — Previous/Next with page info.
 *
 * Props:
 *   page       : number  — 0-based current page
 *   totalPages : number
 *   total      : number  — total items
 *   size       : number  — page size
 *   onPage     : (page: number) => void
 */
export default function DocumentsPagination({ page, totalPages, total, size, onPage }) {
  if (totalPages <= 1) return null;

  const from = page * size + 1;
  const to   = Math.min((page + 1) * size, total);

  return (
    <div className="flex items-center justify-between gap-4 px-1 flex-wrap">
      <p className="text-xs text-gray-500">
        Showing <span className="font-medium text-gray-700">{from}–{to}</span> of{" "}
        <span className="font-medium text-gray-700">{total}</span> documents
      </p>

      <div className="flex items-center gap-2">
        <button
          onClick={() => onPage(page - 1)}
          disabled={page === 0}
          className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg
                     hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed
                     transition-colors"
        >
          ← Prev
        </button>

        <span className="text-sm text-gray-600">
          Page <span className="font-medium">{page + 1}</span> / {totalPages}
        </span>

        <button
          onClick={() => onPage(page + 1)}
          disabled={page >= totalPages - 1}
          className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg
                     hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed
                     transition-colors"
        >
          Next →
        </button>
      </div>
    </div>
  );
}
