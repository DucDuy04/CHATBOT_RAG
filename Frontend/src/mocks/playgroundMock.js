/** Playground mock — mutable sessions + messages */

export let sessions = [
  {
    id: "sess-001",
    chatbotId: "cb-001",
    chatbotName: "Customer Support Bot",
    messageCount: 6,
    lastMessage: "Cảm ơn bạn đã hỗ trợ!",
    createdAt: "2026-05-05T08:30:00Z",
    updatedAt: "2026-05-05T08:45:00Z",
  },
  {
    id: "sess-002",
    chatbotId: "cb-003",
    chatbotName: "Technical Support",
    messageCount: 4,
    lastMessage: "Tôi sẽ thử lại theo hướng dẫn.",
    createdAt: "2026-05-04T14:00:00Z",
    updatedAt: "2026-05-04T14:20:00Z",
  },
  {
    id: "sess-003",
    chatbotId: "cb-001",
    chatbotName: "Customer Support Bot",
    messageCount: 2,
    lastMessage: "Sản phẩm giao trong bao lâu?",
    createdAt: "2026-05-03T10:15:00Z",
    updatedAt: "2026-05-03T10:18:00Z",
  },
];

export const messagesBySession = {
  "sess-001": [
    { id: "msg-001", role: "user",      content: "Tôi muốn đổi trả sản phẩm, quy trình như thế nào?",                     createdAt: "2026-05-05T08:30:00Z", sources: [], latency: null },
    { id: "msg-002", role: "assistant", content: "Để đổi trả sản phẩm, bạn cần chuẩn bị: hóa đơn mua hàng, sản phẩm còn nguyên vẹn trong vòng 30 ngày. Liên hệ hotline 1800-xxx hoặc đến cửa hàng gần nhất.", createdAt: "2026-05-05T08:30:05Z", sources: [{ fileName: "user_guide_v2.pdf", sectionTitle: "Chính sách đổi trả", pages: "12-13" }], latency: 1240 },
    { id: "msg-003", role: "user",      content: "Có thể đổi trả online không?",                                           createdAt: "2026-05-05T08:35:00Z", sources: [], latency: null },
    { id: "msg-004", role: "assistant", content: "Có, bạn có thể yêu cầu đổi trả online qua website hoặc app. Điền form tại mục 'Hỗ trợ sau mua' và đội ngũ sẽ liên hệ trong vòng 24h.",                    createdAt: "2026-05-05T08:35:04Z", sources: [{ fileName: "user_guide_v2.pdf", sectionTitle: "Đổi trả online", pages: "14" }], latency: 980 },
    { id: "msg-005", role: "user",      content: "Cần bao lâu để xử lý hoàn tiền?",                                       createdAt: "2026-05-05T08:40:00Z", sources: [], latency: null },
    { id: "msg-006", role: "assistant", content: "Hoàn tiền thường mất 3–5 ngày làm việc tùy ngân hàng. Với thẻ tín dụng có thể lên đến 7–10 ngày.",                                                          createdAt: "2026-05-05T08:40:03Z", sources: [{ fileName: "user_guide_v2.pdf", sectionTitle: "Chính sách hoàn tiền", pages: "15" }], latency: 870 },
  ],
  "sess-002": [
    { id: "msg-007", role: "user",      content: "API trả lỗi 401 khi gọi từ Postman, tôi đã set header Authorization đúng rồi.", createdAt: "2026-05-04T14:00:00Z", sources: [], latency: null },
    { id: "msg-008", role: "assistant", content: "Lỗi 401 khi đã set header đúng thường do: (1) token đã hết hạn, (2) sai base URL — dev vs staging, (3) thiếu prefix 'Bearer '. Kiểm tra format: `Authorization: Bearer <token>`.", createdAt: "2026-05-04T14:00:06Z", sources: [{ fileName: "api_reference.pdf", sectionTitle: "Authentication", pages: "5-6" }], latency: 1540 },
    { id: "msg-009", role: "user",      content: "Tôi đã kiểm tra và format đúng, nhưng vẫn lỗi.",                          createdAt: "2026-05-04T14:10:00Z", sources: [], latency: null },
    { id: "msg-010", role: "assistant", content: "Bước tiếp theo: kiểm tra expiry time của token bằng jwt.io. Nếu đã hết hạn, gọi endpoint refresh token với `POST /api/auth/refresh`.",                     createdAt: "2026-05-04T14:10:05Z", sources: [{ fileName: "api_reference.pdf", sectionTitle: "Token Refresh", pages: "8" }], latency: 1120 },
  ],
  "sess-003": [
    { id: "msg-011", role: "user",      content: "Sản phẩm giao trong bao lâu?",       createdAt: "2026-05-03T10:15:00Z", sources: [], latency: null },
    { id: "msg-012", role: "assistant", content: "Thời gian giao hàng: nội thành 1–2 ngày, ngoại thành 2–4 ngày, tỉnh xa 3–7 ngày làm việc. Giao hàng nhanh (hỏa tốc) có thể trong ngày với nội thành.", createdAt: "2026-05-03T10:15:04Z", sources: [{ fileName: "user_guide_v2.pdf", sectionTitle: "Giao hàng", pages: "10-11" }], latency: 780 },
  ],
};

// Token mock streaming response
export const mockStreamTokens = [
  "Dựa ", "trên ", "tài ", "liệu ", "được ", "cung ", "cấp, ",
  "tôi ", "có ", "thể ", "trả ", "lời ", "câu ", "hỏi ", "của ", "bạn. ",
  "Đây ", "là ", "phản ", "hồi ", "mock ", "từ ", "hệ ", "thống ",
  "AI ", "playground. ",
];

export const MOCK_SOURCES = [
  { fileName: "user_guide_v2.pdf",        sectionTitle: "Chính sách đổi trả",  pages: "12-13", chunkText: "Quy trình đổi trả sản phẩm trong vòng 30 ngày..." },
  { fileName: "product_catalog_2026.pdf", sectionTitle: "Thông số kỹ thuật",   pages: "45",    chunkText: "Chi tiết thông số và thông tin bảo hành sản phẩm..." },
];
