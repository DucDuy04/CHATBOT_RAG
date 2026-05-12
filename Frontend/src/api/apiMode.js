/**
 * API Mode config — bật/tắt mock API.
 *
 * Ưu tiên:
 *   VITE_USE_MOCK_API=true  → dùng mock
 *   VITE_USE_MOCK_API=false → dùng real API
 *   (không set)             → mock nếu dev, real nếu prod build
 */
export const USE_MOCK_API =
  import.meta.env.VITE_USE_MOCK_API === "true" ||
  (!import.meta.env.VITE_USE_MOCK_API && import.meta.env.DEV === true);

/** Giả lập latency network trong mock */
export const mockDelay = (ms = 300) =>
  new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Tạo pagination response chuẩn từ array đầy đủ.
 * @param {Array}  items - Toàn bộ items đã filter
 * @param {number} page  - Page index (0-based)
 * @param {number} size  - Page size
 * @returns {{ items, page, size, total, totalPages }}
 */
export function createPaginatedResponse(items, page = 0, size = 10) {
  const start = page * size;
  const paged = items.slice(start, start + size);
  return {
    items: paged,
    page,
    size,
    total: items.length,
    totalPages: Math.max(Math.ceil(items.length / size), 1),
  };
}
