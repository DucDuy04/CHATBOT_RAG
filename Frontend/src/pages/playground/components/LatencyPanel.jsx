import { useState } from "react";

/**
 * LatencyPanel — Playground summary: total latency only (details optional).
 */
export default function LatencyPanel({ latency = null }) {
  const [showDetails, setShowDetails] = useState(false);

  const fmtMs = (ms) => (ms != null && Number.isFinite(Number(ms)) ? `${ms} ms` : "—");
  const fmtSeconds = (ms) => {
    if (ms == null || !Number.isFinite(Number(ms))) return "—";
    const sec = Number(ms) / 1000;
    return `${sec.toFixed(2)}s`;
  };

  let total = null;
  let retrieval = null;
  let llm = null;

  if (typeof latency === "number") {
    total = latency;
  } else if (latency && typeof latency === "object") {
    total = latency.total ?? latency.sseTotalMs ?? null;
    retrieval = latency.retrieval ?? null;
    llm = latency.llm ?? latency.llmTotalMs ?? null;
  }

  const hasDetails = retrieval != null || llm != null;

  return (
    <div className="p-3">
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
        Latency
      </p>

      {total == null ? (
        <p className="text-xs text-gray-400 italic">
          Chưa có dữ liệu. Gửi tin nhắn để xem latency.
        </p>
      ) : (
        <>
          <p className="text-sm text-gray-800">
            <span className="text-gray-500">Latency: </span>
            <span className="font-mono font-semibold text-blue-600">{fmtSeconds(total)}</span>
          </p>

          {hasDetails && (
            <div className="mt-2">
              <button
                type="button"
                onClick={() => setShowDetails((v) => !v)}
                className="text-[10px] text-gray-500 hover:text-gray-700 underline"
              >
                {showDetails ? "Ẩn chi tiết" : "Chi tiết"}
              </button>
              {showDetails && (
                <div className="mt-1.5 space-y-1 border-t border-gray-100 pt-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-500">Retrieval</span>
                    <span className="text-xs font-mono text-gray-700">{fmtMs(retrieval)}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-500">LLM</span>
                    <span className="text-xs font-mono text-gray-700">{fmtMs(llm)}</span>
                  </div>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
