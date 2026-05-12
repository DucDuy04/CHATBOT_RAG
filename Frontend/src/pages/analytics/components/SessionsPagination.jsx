export default function SessionsPagination({ page, totalPages, total, size, onPageChange }) {
  const safePage = Number(page) || 0;
  const safeTotalPages = Math.max(Number(totalPages) || 1, 1);
  const safeSize = Math.max(Number(size) || 10, 1);
  const safeTotal = Number(total) || 0;

  const from = safeTotal === 0 ? 0 : safePage * safeSize + 1;
  const to = Math.min((safePage + 1) * safeSize, safeTotal);

  return (
    <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm">
      <p className="text-gray-500">
        Showing <span className="font-medium text-gray-700">{from}-{to}</span> of{" "}
        <span className="font-medium text-gray-700">{safeTotal}</span> sessions
      </p>

      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(safePage - 1)}
          disabled={safePage <= 0}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Prev
        </button>
        <span className="text-gray-600">
          Page <span className="font-semibold text-gray-800">{safePage + 1}</span> / {safeTotalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(safePage + 1)}
          disabled={safePage >= safeTotalPages - 1}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  );
}
