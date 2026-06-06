import { useState, useEffect } from "react";
import useChatbotStore from "../../../stores/chatbotStore";

/**
 * ChatbotToolbar — search + filter bar cho Chatbot Management.
 *
 * Search debounce 300ms: localSearch state → store.setSearch sau 300ms.
 * Status/domain filter: gọi store action trực tiếp (không debounce).
 * Reset filters: xóa cả localSearch lẫn store filters.
 */
export default function ChatbotToolbar() {
  const { filters, domains, setSearch, setStatus, setDomain, resetFilters } =
    useChatbotStore();

  const [localSearch, setLocalSearch] = useState(filters.search);

  // Debounce: sau 300ms mới gọi setSearch để tránh fetch mỗi keystroke
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(localSearch);
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch, setSearch]);

  // Sync localSearch nếu store reset về "" (e.g. sau resetFilters)
  useEffect(() => {
    // Đồng bộ local state với store — setLocalSearch là React setState nhưng
    // chỉ chạy khi filters.search thay đổi từ bên ngoài, không phải cascade.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (filters.search === "") setLocalSearch("");
  }, [filters.search]);

  const hasFilters = localSearch || filters.status || filters.domain;

  return (
    <div className="flex flex-wrap gap-2.5 items-center">
      {/* Search input */}
      <div className="relative flex-1 min-w-[200px]">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none">
          🔍
        </span>
        <input
          type="text"
          value={localSearch}
          onChange={(e) => setLocalSearch(e.target.value)}
          placeholder="Search chatbots..."
          className="w-full pl-8 pr-3 py-2 text-sm border border-gray-300 rounded-lg
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     placeholder:text-gray-400"
        />
      </div>

      {/* Status filter */}
      <select
        value={filters.status}
        onChange={(e) => setStatus(e.target.value)}
        className="px-3 py-2 text-sm border border-gray-300 rounded-lg bg-white
                   focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                   text-gray-700"
      >
        <option value="">All statuses</option>
        <option value="ACTIVE">Active</option>
        <option value="INACTIVE">Inactive</option>
      </select>

      {/* Domain filter — options từ dữ liệu đã load */}
      <select
        value={filters.domain}
        onChange={(e) => setDomain(e.target.value)}
        className="px-3 py-2 text-sm border border-gray-300 rounded-lg bg-white
                   focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                   text-gray-700"
      >
        <option value="">All domains</option>
        {domains.map((d) => (
          <option key={d} value={d}>
            {d.charAt(0).toUpperCase() + d.slice(1)}
          </option>
        ))}
      </select>

      {/* Clear filters button — chỉ hiện khi có filter đang active */}
      {hasFilters && (
        <button
          onClick={() => {
            setLocalSearch("");
            resetFilters();
          }}
          className="px-3 py-2 text-sm text-gray-500 border border-gray-300 rounded-lg
                     hover:bg-gray-50 hover:text-gray-700 transition-colors flex items-center gap-1"
          title="Clear all filters"
        >
          <span aria-hidden="true">✕</span>
          Clear
        </button>
      )}
    </div>
  );
}
