/**
 * TokenUsagePanel — Playground debug: per-request token usage from backend.
 *
 * Props:
 *   tokenUsage — object from SSE done / compare result
 */
export default function TokenUsagePanel({ tokenUsage = null }) {
  const fmt = (n) => (n != null && Number.isFinite(Number(n)) ? String(n) : "—");
  const fmtNull = (n) => (n == null ? "—" : fmt(n));

  if (!tokenUsage) {
    return (
      <div className="p-3 border-t border-gray-100">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
          Token usage
        </p>
        <p className="text-xs text-gray-400 italic">
          Chưa có dữ liệu. Gửi tin nhắn hoặc chạy Compare để xem token/request.
        </p>
      </div>
    );
  }

  const rows = [
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
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">
        Token usage
      </p>
      <div className="space-y-1.5">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-start justify-between gap-2">
            <span className="text-xs text-gray-500 shrink-0">{label}</span>
            <span className="text-xs font-mono text-gray-800 text-right break-all">
              {typeof value === "string" ? value || "—" : fmtNull(value)}
            </span>
          </div>
        ))}
      </div>
      {tokenUsage.actualTotalTokens == null && tokenUsage.estimatedInputTokens != null && (
        <p className="text-[10px] text-amber-700 mt-2 leading-snug">
          Actual usage chưa có từ provider — số estimate có thể thấp hơn TPM thực tế của Groq.
        </p>
      )}
    </div>
  );
}
