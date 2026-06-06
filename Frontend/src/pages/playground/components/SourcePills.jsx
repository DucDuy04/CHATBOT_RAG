/**
 * SourcePills — row of clickable pills below an assistant message.
 * Each pill shows the source file name and an optional relevance score.
 * Clicking a pill sets it as selected (used to highlight in RetrievalPanel).
 *
 * Props:
 *   sources        — array of source objects { fileName, sectionTitle, score?, ... }
 *   selectedSource — currently highlighted source object or null
 *   onPillClick    — (source | null) => void
 */
export default function SourcePills({ sources = [], selectedSource, onPillClick }) {
  if (!sources || sources.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-1.5 mt-2 px-1">
      {sources.map((src, idx) => {
        const isSelected =
          selectedSource &&
          selectedSource.fileName === src.fileName &&
          selectedSource.sectionTitle === src.sectionTitle;

        return (
          <button
            key={idx}
            onClick={() => onPillClick(isSelected ? null : src)}
            className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-xs border transition-colors ${
              isSelected
                ? "bg-blue-600 text-white border-blue-600"
                : "bg-white text-gray-600 border-gray-300 hover:border-blue-400 hover:text-blue-600"
            }`}
            title={src.chunkText || src.sectionTitle || src.fileName}
          >
            <span>📄</span>
            <span className="max-w-[120px] truncate">{src.fileName}</span>
            {src.score != null && (
              <span
                className={`font-mono text-xs ${
                  isSelected ? "text-blue-200" : "text-gray-400"
                }`}
              >
                {(src.score * 100).toFixed(0)}%
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
