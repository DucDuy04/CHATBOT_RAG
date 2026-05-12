/** Dashboard mock — read-only stats */

export const dashboardSummary = {
  activeChatbots: 5,
  activeChatbotsDelta: +2,
  messages7d: 1284,
  messages7dDelta: +18.4,
  avgSatisfaction: 87.3,
  avgSatisfactionDelta: +2.1,
  documentCount: 10,
  documentCountDelta: +3,
};

// Last 7 days message volume — ISO date + count
export const messageVolume7d = [
  { date: "2026-04-29", count: 142 },
  { date: "2026-04-30", count: 189 },
  { date: "2026-05-01", count: 203 },
  { date: "2026-05-02", count: 178 },
  { date: "2026-05-03", count: 211 },
  { date: "2026-05-04", count: 196 },
  { date: "2026-05-05", count: 165 },
];

export const topChatbots = [
  { id: "cb-001", name: "Customer Support Bot", messageCount: 1840, satisfaction: 91.2 },
  { id: "cb-002", name: "FAQ Assistant",         messageCount: 924,  satisfaction: 88.5 },
  { id: "cb-003", name: "Technical Support",     messageCount: 567,  satisfaction: 85.0 },
  { id: "cb-004", name: "Sales Assistant",       messageCount: 312,  satisfaction: 89.3 },
  { id: "cb-005", name: "HR Helpdesk",           messageCount: 203,  satisfaction: 82.7 },
];

export const activityEvents = [
  { id: "act-001", type: "chatbot_created",    actor: "admin@example.com", target: "Customer Support Bot", createdAt: "2026-05-05T09:12:00Z" },
  { id: "act-002", type: "document_indexed",   actor: "system",             target: "user_guide_v2.pdf",   createdAt: "2026-05-05T09:05:00Z" },
  { id: "act-003", type: "chatbot_updated",    actor: "admin@example.com", target: "FAQ Assistant",        createdAt: "2026-05-04T16:30:00Z" },
  { id: "act-004", type: "document_uploaded",  actor: "editor@example.com",target: "hr_policy_2026.pdf",  createdAt: "2026-05-05T08:00:00Z" },
  { id: "act-005", type: "api_key_generated",  actor: "admin@example.com", target: "sk_live_...a4f2",     createdAt: "2026-05-03T11:00:00Z" },
  { id: "act-006", type: "document_failed",    actor: "system",             target: "corrupted_report.pdf",createdAt: "2026-05-03T15:02:00Z" },
  { id: "act-007", type: "chatbot_updated",    actor: "admin@example.com", target: "Technical Support",   createdAt: "2026-05-02T10:00:00Z" },
  { id: "act-008", type: "document_indexed",   actor: "system",             target: "api_reference.pdf",   createdAt: "2026-04-28T14:40:00Z" },
  { id: "act-009", type: "chatbot_deactivated",actor: "admin@example.com", target: "Product Guide",       createdAt: "2026-04-25T08:35:00Z" },
  { id: "act-010", type: "member_invited",     actor: "admin@example.com", target: "newuser@example.com", createdAt: "2026-04-24T14:00:00Z" },
];
