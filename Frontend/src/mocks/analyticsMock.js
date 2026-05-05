/** Analytics mock — read-only stats + mutable feedback */

export const analyticsSummary = {
  totalMessages: 3846,
  totalMessagesDelta: +14.2,
  uniqueSessions: 891,
  uniqueSessionsDelta: +9.8,
  avgSatisfaction: 87.3,
  avgSatisfactionDelta: +2.1,
  fallbackRate: 8.4,
  fallbackRateDelta: -1.3,
};

// Daily message + session counts (last 14 days)
export const dailyData = [
  { date: "2026-04-22", messages: 201, sessions: 48 },
  { date: "2026-04-23", messages: 178, sessions: 41 },
  { date: "2026-04-24", messages: 245, sessions: 56 },
  { date: "2026-04-25", messages: 189, sessions: 45 },
  { date: "2026-04-26", messages: 156, sessions: 36 },
  { date: "2026-04-27", messages: 132, sessions: 30 },
  { date: "2026-04-28", messages: 267, sessions: 62 },
  { date: "2026-04-29", messages: 298, sessions: 71 },
  { date: "2026-04-30", messages: 312, sessions: 74 },
  { date: "2026-05-01", messages: 285, sessions: 68 },
  { date: "2026-05-02", messages: 271, sessions: 64 },
  { date: "2026-05-03", messages: 305, sessions: 73 },
  { date: "2026-05-04", messages: 289, sessions: 69 },
  { date: "2026-05-05", messages: 218, sessions: 54 },
];

export const byChatbot = [
  { chatbotId: "cb-001", chatbotName: "Customer Support Bot", messageCount: 1840, share: 47.8 },
  { chatbotId: "cb-002", chatbotName: "FAQ Assistant",         messageCount: 924,  share: 24.0 },
  { chatbotId: "cb-003", chatbotName: "Technical Support",     messageCount: 567,  share: 14.7 },
  { chatbotId: "cb-004", chatbotName: "Sales Assistant",       messageCount: 312,  share: 8.1  },
  { chatbotId: "cb-005", chatbotName: "HR Helpdesk",           messageCount: 203,  share: 5.3  },
];

export const unansweredQuestions = [
  { id: "uq-001", question: "Chính sách bảo hành quốc tế như thế nào?",         chatbotName: "Customer Support Bot", count: 12, lastAskedAt: "2026-05-05T07:30:00Z" },
  { id: "uq-002", question: "API có hỗ trợ GraphQL không?",                       chatbotName: "Technical Support",     count: 8,  lastAskedAt: "2026-05-04T16:00:00Z" },
  { id: "uq-003", question: "Thủ tục xin nghỉ thai sản cho nhân viên hợp đồng?", chatbotName: "HR Helpdesk",           count: 6,  lastAskedAt: "2026-05-04T10:00:00Z" },
  { id: "uq-004", question: "Có gói enterprise không?",                            chatbotName: "Sales Assistant",       count: 5,  lastAskedAt: "2026-05-03T14:30:00Z" },
  { id: "uq-005", question: "Tích hợp với Salesforce như thế nào?",               chatbotName: "Technical Support",     count: 4,  lastAskedAt: "2026-05-03T09:00:00Z" },
  { id: "uq-006", question: "Miễn phí vận chuyển cho đơn từ bao nhiêu?",          chatbotName: "Customer Support Bot", count: 4,  lastAskedAt: "2026-05-02T11:00:00Z" },
  { id: "uq-007", question: "Phiên bản mobile app có những tính năng gì?",        chatbotName: "FAQ Assistant",         count: 3,  lastAskedAt: "2026-05-02T08:30:00Z" },
];

export let analyticsSessions = [
  { id: "as-001", chatbotId: "cb-001", chatbotName: "Customer Support Bot", messageCount: 5, rating: 5, createdAt: "2026-05-05T08:30:00Z" },
  { id: "as-002", chatbotId: "cb-002", chatbotName: "FAQ Assistant",         messageCount: 3, rating: 4, createdAt: "2026-05-05T08:00:00Z" },
  { id: "as-003", chatbotId: "cb-003", chatbotName: "Technical Support",     messageCount: 7, rating: 3, createdAt: "2026-05-04T16:00:00Z" },
  { id: "as-004", chatbotId: "cb-001", chatbotName: "Customer Support Bot", messageCount: 2, rating: 5, createdAt: "2026-05-04T15:00:00Z" },
  { id: "as-005", chatbotId: "cb-004", chatbotName: "Sales Assistant",       messageCount: 4, rating: 4, createdAt: "2026-05-04T14:00:00Z" },
  { id: "as-006", chatbotId: "cb-005", chatbotName: "HR Helpdesk",           messageCount: 3, rating: 2, createdAt: "2026-05-04T13:00:00Z" },
  { id: "as-007", chatbotId: "cb-001", chatbotName: "Customer Support Bot", messageCount: 6, rating: 5, createdAt: "2026-05-03T11:00:00Z" },
  { id: "as-008", chatbotId: "cb-002", chatbotName: "FAQ Assistant",         messageCount: 2, rating: 4, createdAt: "2026-05-03T10:00:00Z" },
  { id: "as-009", chatbotId: "cb-003", chatbotName: "Technical Support",     messageCount: 8, rating: 1, createdAt: "2026-05-02T16:00:00Z" },
  { id: "as-010", chatbotId: "cb-001", chatbotName: "Customer Support Bot", messageCount: 3, rating: 5, createdAt: "2026-05-02T09:00:00Z" },
];

export const sessionMessages = {
  "as-001": [
    { id: "am-001", role: "user",      content: "Tôi bị giao nhầm sản phẩm, cần đổi lại.",    rating: null,  sources: [] },
    { id: "am-002", role: "assistant", content: "Xin lỗi về sự bất tiện này! Vui lòng cung cấp mã đơn hàng và ảnh sản phẩm nhận được. Đội ngũ sẽ xử lý trong 24h.", rating: 5, sources: [{ fileName: "user_guide_v2.pdf" }] },
    { id: "am-003", role: "user",      content: "Mã đơn: #12345, ảnh đính kèm.",              rating: null,  sources: [] },
    { id: "am-004", role: "assistant", content: "Đã ghi nhận. Bạn sẽ nhận email xác nhận đổi hàng trong vòng 2 tiếng.",                         rating: 5, sources: [] },
  ],
};
