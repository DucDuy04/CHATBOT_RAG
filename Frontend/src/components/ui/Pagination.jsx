function Pagination({
  page = 1,
  totalPages = 1,
  showing = 0,
  total = 0,
  onPageChange,
}) {
  return (
    <div>
      <span>{`Showing ${showing} of ${total} items`}</span>
      <div>
        <button type="button" onClick={() => onPageChange?.(page - 1)} disabled={page <= 1}>
          Previous
        </button>
        <span>{`Page ${page} of ${totalPages}`}</span>
        <button
          type="button"
          onClick={() => onPageChange?.(page + 1)}
          disabled={page >= totalPages}
        >
          Next
        </button>
      </div>
    </div>
  );
}

export default Pagination;
