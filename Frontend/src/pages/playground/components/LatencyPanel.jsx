/**
 * LatencyPanel — right panel showing retrieval / LLM / total latency.
 *
 * Props:
 *   latency — number (total ms) | { total, retrieval, llm } | null
 *
 * The API may return a single number or a breakdown object; both shapes are handled gracefully.
 */
export default function LatencyPanel({ latency = null }) {
  const fmt = (ms) => (ms != null ? `${ms} ms` : "—");

  let total = null;
  let retrieval = null;
  let llm = null;

  if (typeof latency === "number") {
    total = latency;
  } else if (latency && typeof latency === "object") {
    total = latency.total ?? null;
    retrieval = latency.retrieval ?? null;
    llm = latency.llm ?? null;
  }

  return (
    <div className="p-3">
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">
        Latency
      </p>

      {total == null ? (
        <p className="text-xs text-gray-400 italic">
          Chưa có dữ liệu. Gửi tin nhắn để xem latency.
        </p>
      ) : (
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs text-gray-500">Retrieval</span>
            <span className="text-xs font-mono text-gray-700">{fmt(retrieval)}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-xs text-gray-500">LLM</span>
            <span className="text-xs font-mono text-gray-700">{fmt(llm)}</span>
          </div>
          <div className="flex items-center justify-between border-t border-gray-100 pt-2 mt-1">
            <span className="text-xs font-semibold text-gray-700">Total</span>
            <span className="text-xs font-mono font-semibold text-blue-600">
              {fmt(total)}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
