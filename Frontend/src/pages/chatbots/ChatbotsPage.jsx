import { useState, useEffect } from "react";
import { useLayout } from "../../contexts/LayoutContext";
import useChatbotStore from "../../stores/chatbotStore";
import ChatbotToolbar from "./components/ChatbotToolbar";
import ChatbotTable from "./components/ChatbotTable";
import CreateChatbotModal from "./components/CreateChatbotModal";
import Pagination from "./components/Pagination";

/**
 * ChatbotsPage — trang quản lý chatbots tại /chatbots.
 *
 * - Header rightSlot: "+ New chatbot" button (qua useLayout).
 * - Fetch khi mount và khi filters/pagination thay đổi.
 * - Search debounce được xử lý trong ChatbotToolbar.
 * - Mỗi section có loading/error state qua chatbotStore.
 */
export default function ChatbotsPage() {
  const { setRightSlot, clearRightSlot } = useLayout();
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Subscribe to individual values (không dùng whole object để tránh re-render thừa)
  const items        = useChatbotStore((s) => s.items);
  const loading      = useChatbotStore((s) => s.loading);
  const error        = useChatbotStore((s) => s.error);
  const search       = useChatbotStore((s) => s.filters.search);
  const status       = useChatbotStore((s) => s.filters.status);
  const domain       = useChatbotStore((s) => s.filters.domain);
  const page         = useChatbotStore((s) => s.pagination.page);
  const size         = useChatbotStore((s) => s.pagination.size);
  const total        = useChatbotStore((s) => s.pagination.total);
  const totalPages   = useChatbotStore((s) => s.pagination.totalPages);
  const fetchChatbots = useChatbotStore((s) => s.fetchChatbots);
  const setPage      = useChatbotStore((s) => s.setPage);

  // ─── Fetch khi mount + khi filter/pagination đổi ──────────────────────────
  useEffect(() => {
    fetchChatbots();
  }, [fetchChatbots, search, status, domain, page, size]);

  // ─── Inject "+ New chatbot" vào Header rightSlot ──────────────────────────
  useEffect(() => {
    setRightSlot(
      <button
        onClick={() => setShowCreateModal(true)}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium
                   bg-blue-600 text-white rounded-lg hover:bg-blue-700 active:bg-blue-800
                   transition-colors"
      >
        <span aria-hidden="true">+</span>
        New chatbot
      </button>
    );
  }, [setRightSlot]);

  useEffect(() => {
    return () => clearRightSlot();
  }, [clearRightSlot]);

  // ─── Handlers ─────────────────────────────────────────────────────────────

  function handleCreated() {
    // Sau khi tạo thành công, refresh list để thấy item mới
    fetchChatbots();
  }

  // ─── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="space-y-4 p-1">
      {/* Toolbar: search + filters */}
      <ChatbotToolbar />

      {/* Table */}
      <ChatbotTable
        items={items}
        loading={loading}
        error={error}
        onRetry={fetchChatbots}
      />

      {/* Pagination */}
      <Pagination
        page={page}
        totalPages={totalPages}
        total={total}
        size={size}
        onPageChange={setPage}
      />

      {/* Create modal */}
      <CreateChatbotModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}
