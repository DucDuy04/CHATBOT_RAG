import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import CompareConfigPanel from "./CompareConfigPanel";
import SkeletonLoader from "../../../components/common/SkeletonLoader";
import EmptyState from "../../../components/common/EmptyState";

function AnswerColumn({ title, result, loading, configSummary, topK }) {
  if (loading) {
    return (
      <div className="rounded-lg border bg-white p-4 min-h-[200px]">
        <p className="text-xs font-semibold text-gray-500 mb-2">{title}</p>
        <SkeletonLoader variant="line" count={6} />
      </div>
    );
  }

  if (!result) {
    return (
      <div className="rounded-lg border border-dashed border-gray-200 bg-white min-h-[160px]">
        <EmptyState
          icon="⚖️"
          title={title}
          message="Chạy compare để xem kết quả."
          className="py-8"
        />
      </div>
    );
  }

  const answer =
    typeof result.answer === "string"
      ? result.answer
      : result.answer != null
        ? String(result.answer)
        : "";

  const rawSources = Array.isArray(result.sources) ? result.sources : [];
  const cap =
    topK != null && Number.isFinite(Number(topK)) && Number(topK) > 0
      ? Math.min(50, Math.max(1, Math.floor(Number(topK))))
      : null;
  const sources = cap != null ? rawSources.slice(0, cap) : rawSources;
  const hiddenCount = cap != null ? Math.max(0, rawSources.length - sources.length) : 0;
  const latency = result.latency;

  return (
    <div className="rounded-lg border bg-white p-4 flex flex-col gap-3 min-h-[200px]">
      <p className="text-xs font-semibold text-gray-500">{title}</p>

      <div className="text-xs bg-gray-50 border rounded p-2 font-mono text-gray-700 whitespace-pre-wrap break-all">
        {configSummary}
      </div>

      <div className="prose prose-sm max-w-none">
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          components={{
            p: ({ ...props }) => (
              <p className="mb-2 last:mb-0 break-words" style={{ overflowWrap: "anywhere" }} {...props} />
            ),
          }}
        >
          {answer || "_Không có nội dung trả lời._"}
        </ReactMarkdown>
      </div>

      {latency != null && (
        <p className="text-xs text-gray-500">
          Latency:{" "}
          <span className="font-mono text-gray-700">{String(latency)} ms</span>
        </p>
      )}

      {sources.length > 0 ? (
        <div className="text-xs border-t pt-2">
          <p className="font-semibold text-gray-600 mb-1">Sources</p>
          {cap != null && (
            <p className="text-[10px] text-gray-500 mb-1">
              Showing top {cap} source{cap === 1 ? "" : "s"}
              {hiddenCount > 0 ? ` (${hiddenCount} more from retrieval hidden in MVP view).` : "."}
            </p>
          )}
          <ul className="space-y-1 text-gray-600">
            {sources.map((src, i) => (
              <li key={i} className="truncate" title={src.chunkText}>
                {src.fileName}
                {src.sectionTitle ? ` · ${src.sectionTitle}` : ""}
                {src.score != null ? ` · ${(src.score * 100).toFixed(0)}%` : ""}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="text-xs text-gray-500 border-t pt-2">
          No sources returned for this compare branch.
        </p>
      )}
    </div>
  );
}

/**
 * ComparePane — query + config A/B + run + side-by-side results.
 */
export default function ComparePane({
  compareInput,
  onCompareInputChange,
  compareConfigA,
  compareConfigB,
  onCompareConfigAChange,
  onCompareConfigBChange,
  compareLoading,
  compareError,
  compareResult,
  onRunCompare,
  runDisabled,
  globalPromptActive,
}) {
  const fmtCfg = (cfg, label) => {
    const lines = [
      `${label}: temp=${Number(cfg.temperature).toFixed(2)} topK=${cfg.topK} maxTok=${cfg.maxTokens}`,
    ];
    const sp = cfg.systemPrompt?.trim();
    if (sp) lines.push(`local systemPrompt: ${sp.slice(0, 120)}${sp.length > 120 ? "…" : ""}`);
    return lines.join("\n");
  };

  const mergedError = compareError;
  const resA = compareResult?.configA ?? compareResult?.config_a;
  const resB = compareResult?.configB ?? compareResult?.config_b;

  return (
    <div className="flex flex-col h-full min-h-0 overflow-hidden bg-gray-50">
      <div className="shrink-0 p-3 border-b bg-white space-y-3">
        {globalPromptActive && (
          <p className="text-xs text-blue-700 bg-blue-50 border border-blue-100 rounded px-2 py-1">
            Prompt Builder override đang bật — áp dụng khi cột System prompt trống.
          </p>
        )}
        <p className="text-[11px] text-gray-600 leading-snug border border-gray-200 rounded-lg px-2 py-1.5 bg-gray-50">
          Compare dùng để thử cấu hình A/B. Backend hiện có thể chưa áp dụng sâu mọi override (temperature/topK/maxTokens)
          nên câu trả lời A và B có thể giống nhau. Compare không lưu phiên chat — danh sách Sessions bên trái không đổi sau khi chạy.
          Nguồn (nếu có) hiển thị dưới từng cột và đồng bộ panel Sources bên phải.
        </p>
        <div>
          <label className="text-xs font-medium text-gray-600 block mb-1">
            Compare query
          </label>
          <textarea
            value={compareInput}
            onChange={(e) => onCompareInputChange(e.target.value)}
            rows={2}
            placeholder="Nhập câu hỏi dùng chung cho Config A và B..."
            disabled={compareLoading}
            className="w-full px-3 py-2 text-sm border rounded-lg resize-none focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <button
          type="button"
          onClick={onRunCompare}
          disabled={runDisabled}
          className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {compareLoading ? "Đang so sánh…" : "Run compare"}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <CompareConfigPanel
            label="Config A"
            config={compareConfigA}
            onChange={onCompareConfigAChange}
          />
          <CompareConfigPanel
            label="Config B"
            config={compareConfigB}
            onChange={onCompareConfigBChange}
          />
        </div>

        {mergedError && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {mergedError}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pb-4">
          <AnswerColumn
            title="Answer A"
            result={resA}
            loading={compareLoading}
            configSummary={fmtCfg(compareConfigA, "A")}
            topK={compareConfigA?.topK}
          />
          <AnswerColumn
            title="Answer B"
            result={resB}
            loading={compareLoading}
            configSummary={fmtCfg(compareConfigB, "B")}
            topK={compareConfigB?.topK}
          />
        </div>
      </div>
    </div>
  );
}
