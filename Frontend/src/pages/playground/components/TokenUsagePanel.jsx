import { useState } from "react";

/**
 * TokenUsagePanel — Playground summary: total tokens only (details optional).
 */
export default function TokenUsagePanel({ tokenUsage = null }) {
  const [showDetails, setShowDetails] = useState(false);

  const fmt = (n) => (n != null && Number.isFinite(Number(n)) ? Number(n).toLocaleString() : null);

  const resolveTotalTokens = () => {
    if (!tokenUsage) return null;
    if (tokenUsage.actualTotalTokens != null) return tokenUsage.actualTotalTokens;
    if (tokenUsage.estimatedTotalRequestTokens != null) {
      return tokenUsage.estimatedTotalRequestTokens;
    }
    const prompt = tokenUsage.actualPromptTokens;
    const completion = tokenUsage.actualCompletionTokens;
    if (prompt != null && completion != null) return prompt + completion;
    return null;
  };

  const totalTokens = resolveTotalTokens();
  const totalLabel =
    totalTokens != null ? fmt(totalTokens) : tokenUsage ? "N/A" : null;

  if (!tokenUsage) {
    return (
      <div className="p-3 border-t border-gray-100">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
          Tokens
        </p>
        <p className="text-xs text-gray-400 italic">
          Chưa có dữ liệu. Gửi tin nhắn hoặc chạy Compare để xem token.
        </p>
      </div>
    );
  }

  const detailRows = [
    ["Estimated input tokens", tokenUsage.estimatedInputTokens],
    ["Reserved output (maxTokens)", tokenUsage.reservedOutputTokens],
    ["Estimated total request", tokenUsage.estimatedTotalRequestTokens],
    ["Actual prompt tokens", tokenUsage.actualPromptTokens],
    ["Actual completion tokens", tokenUsage.actualCompletionTokens],
    ["Actual total tokens", tokenUsage.actualTotalTokens],
    ["Provider requested (error)", tokenUsage.providerRequestedTokens],
    ["Context chars", tokenUsage.contextChars],
    ["History chars", tokenUsage.historyChars],
    ["Final contexts", tokenUsage.finalContexts],
    ["Context Top-N", tokenUsage.contextTopN],
    ["Model", tokenUsage.model],
    ["Provider", tokenUsage.provider],
  ];

  return (
    <div className="p-3 border-t border-gray-100">
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
        Tokens
      </p>
      <p className="text-sm text-gray-800">
        <span className="text-gray-500">Tokens: </span>
        <span className="font-mono font-semibold text-gray-900">{totalLabel}</span>
      </p>

      {tokenUsage.actualTotalTokens == null && tokenUsage.estimatedInputTokens != null && (
        <p className="text-[10px] text-amber-700 mt-1 leading-snug">
          Provider chưa trả actual usage — hiển thị estimate hoặc N/A.
        </p>
      )}

      <button
        type="button"
        onClick={() => setShowDetails((v) => !v)}
        className="mt-2 text-[10px] text-gray-500 hover:text-gray-700 underline"
      >
        {showDetails ? "Ẩn chi tiết" : "Chi tiết"}
      </button>

      {showDetails && (
        <div className="mt-1.5 space-y-1 border-t border-gray-100 pt-1.5">
          {detailRows.map(([label, value]) => (
            <div key={label} className="flex items-start justify-between gap-2">
              <span className="text-xs text-gray-500 shrink-0">{label}</span>
              <span className="text-xs font-mono text-gray-800 text-right break-all">
                {typeof value === "string" ? value || "—" : value != null ? String(value) : "—"}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
