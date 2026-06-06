import { useState } from "react";
import EmptyState from "../../../components/common/EmptyState";

/**
 * RetrievalPanel — right panel showing top-K retrieved chunks.
 * Highlights the item matching selectedSource when a SourcePill is clicked.
 *
 * Props:
 *   sources       — array of source objects from last assistant response
 *   selectedSource — highlighted source or null
 *   onSourceSelect — (source | null) => void
 *   compareMode    — when true, empty-state copy matches compare flow (no session persist)
 */
export default function RetrievalPanel({
  sources = [],
  selectedSource,
  onSourceSelect,
  compareMode = false,
}) {
  const [collapsed, setCollapsed] = useState(false);

  const toggleLabel = collapsed ? "Mở rộng danh sách nguồn" : "Thu gọn danh sách nguồn";

  return (
    <div className="p-3">
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        aria-expanded={!collapsed}
        aria-label={toggleLabel}
        className="w-full flex items-center gap-1.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide hover:text-gray-700"
      >
        <span className="shrink-0 text-[10px] leading-none" aria-hidden="true">
          {collapsed ? "▶" : "▼"}
        </span>
        <span>Nguồn ({sources.length})</span>
      </button>

      {sources.length === 0 ? (
        <EmptyState
          icon="🔍"
          title={compareMode ? "No sources for compare" : "No sources for this answer"}
          message={
            compareMode
              ? "No sources returned for compare result (retrieval may be empty or backend omitted sources)."
              : "Nguồn tham chiếu sẽ hiển thị khi retrieval trả về dữ liệu."
          }
          className="py-6"
        />
      ) : collapsed ? null : (
        <div className="mt-2 max-h-[360px] overflow-y-auto space-y-2 pr-0.5">
          {sources.map((src, idx) => {
            const isSelected =
              selectedSource &&
              selectedSource.fileName === src.fileName &&
              selectedSource.sectionTitle === src.sectionTitle;

            return (
              <button
                key={idx}
                onClick={() => onSourceSelect(isSelected ? null : src)}
                className={`w-full text-left p-2 rounded-lg border text-xs transition-colors ${
                  isSelected
                    ? "border-blue-400 bg-blue-50"
                    : "border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50"
                }`}
              >
                {/* File name + score */}
                <div className="flex items-center justify-between gap-1 mb-1">
                  <span className="font-medium text-gray-700 truncate flex-1">
                    {src.fileName}
                  </span>
                  {src.score != null && (
                    <span className="shrink-0 font-mono text-blue-600 bg-blue-100 px-1 py-0.5 rounded text-xs">
                      {(src.score * 100).toFixed(0)}%
                    </span>
                  )}
                </div>

                {src.sectionTitle && (
                  <p className="text-gray-500 truncate">{src.sectionTitle}</p>
                )}
                {src.pages && (
                  <p className="text-gray-400 mt-0.5">Trang {src.pages}</p>
                )}
                {src.chunkText && (
                  <p className="text-gray-400 line-clamp-2 mt-1">{src.chunkText}</p>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
