export default function FeedbackSummaryCards({ thumbsUp = 0, thumbsDown = 0 }) {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Thumbs up</p>
        <p className="mt-2 text-3xl font-bold text-green-600">{thumbsUp}</p>
      </div>
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Thumbs down</p>
        <p className="mt-2 text-3xl font-bold text-red-600">{thumbsDown}</p>
      </div>
    </div>
  );
}
