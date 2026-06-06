import { useRef } from "react";

const TYPE_OPTIONS = [
  { value: "", label: "All types" },
  { value: "PDF",  label: "PDF" },
  { value: "TXT",  label: "TXT" },
  { value: "DOCX", label: "DOCX" },
];

const STATUS_OPTIONS = [
  { value: "",           label: "All statuses" },
  { value: "INDEXED",    label: "Indexed" },
  { value: "PROCESSING", label: "Processing" },
  { value: "FAILED",     label: "Failed" },
];

/**
 * DocumentsToolbar — search + type/chatbot/status filters.
 *
 * Props:
 *   filters      : { search, type, chatbotId, status }
 *   onFilter     : (key, value) => void
 *   chatbots     : [{ id, name }]   — for chatbot dropdown
 *   onClear      : () => void
 */
export default function DocumentsToolbar({ filters, onFilter, chatbots = [], onClear }) {
  const debounceRef = useRef(null);

  function handleSearchChange(e) {
    const val = e.target.value;
    // Update raw input immediately for controlled feel, debounce API call via onFilter
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      onFilter("search", val);
    }, 300);
    // Pass raw value through a separate synthetic path so input stays responsive
    onFilter("_searchRaw", val);
  }

  const hasActiveFilter =
    filters.search || filters.type || filters.chatbotId || filters.status;

  const selectClass =
    "px-3 py-2 text-sm border border-gray-300 rounded-lg bg-white focus:outline-none " +
    "focus:ring-2 focus:ring-blue-500 focus:border-blue-500 min-w-[120px]";

  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* Search */}
      <div className="relative flex-1 min-w-[180px]">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none">
          🔍
        </span>
        <input
          type="text"
          placeholder="Search documents…"
          defaultValue={filters.search}
          onChange={handleSearchChange}
          className="w-full pl-8 pr-3 py-2 text-sm border border-gray-300 rounded-lg bg-white
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
        />
      </div>

      {/* Type filter */}
      <select
        value={filters.type}
        onChange={(e) => onFilter("type", e.target.value)}
        className={selectClass}
      >
        {TYPE_OPTIONS.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>

      {/* Chatbot filter */}
      <select
        value={filters.chatbotId}
        onChange={(e) => onFilter("chatbotId", e.target.value)}
        className={selectClass}
      >
        <option value="">All chatbots</option>
        {chatbots.map((c) => (
          <option key={c.id} value={c.id}>{c.name}</option>
        ))}
      </select>

      {/* Status filter */}
      <select
        value={filters.status}
        onChange={(e) => onFilter("status", e.target.value)}
        className={selectClass}
      >
        {STATUS_OPTIONS.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>

      {/* Clear */}
      {hasActiveFilter && (
        <button
          type="button"
          onClick={onClear}
          className="px-3 py-2 text-sm text-gray-500 border border-gray-300 rounded-lg
                     hover:bg-gray-50 hover:text-gray-700 transition-colors whitespace-nowrap"
        >
          ✕ Clear
        </button>
      )}
    </div>
  );
}
