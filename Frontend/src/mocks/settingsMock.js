/** Settings mock — mutable profile, apiKeys, team */

export let profile = {
  id: "user-admin-001",
  name: "Nguyễn Văn Admin",
  email: "admin@example.com",
  role: "ADMIN",
  avatarUrl: null,
  timezone: "Asia/Ho_Chi_Minh",
  language: "vi",
  createdAt: "2026-01-01T00:00:00Z",
};

export let apiKeys = [
  {
    id: "key-001",
    name: "Production Widget Key",
    maskedKey: "sk_live_****a4f2",
    status: "ACTIVE",
    lastUsedAt: "2026-05-05T08:00:00Z",
    createdAt: "2026-03-15T10:00:00Z",
  },
  {
    id: "key-002",
    name: "Staging Test Key",
    maskedKey: "sk_live_****c8d1",
    status: "ACTIVE",
    lastUsedAt: "2026-05-04T14:30:00Z",
    createdAt: "2026-04-01T09:00:00Z",
  },
  {
    id: "key-003",
    name: "Old Demo Key",
    maskedKey: "sk_live_****b2e9",
    status: "INACTIVE",
    lastUsedAt: "2026-04-10T11:00:00Z",
    createdAt: "2026-02-20T15:00:00Z",
  },
];

export let teamMembers = [
  {
    id: "user-admin-001",
    name: "Nguyễn Văn Admin",
    email: "admin@example.com",
    role: "ADMIN",
    status: "ACTIVE",
    joinedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "user-002",
    name: "Trần Thị Biên",
    email: "editor@example.com",
    role: "EDITOR",
    status: "ACTIVE",
    joinedAt: "2026-02-01T00:00:00Z",
  },
  {
    id: "user-003",
    name: "Lê Văn Cường",
    email: "viewer@example.com",
    role: "VIEWER",
    status: "ACTIVE",
    joinedAt: "2026-03-01T00:00:00Z",
  },
  {
    id: "user-004",
    name: null,
    email: "pending1@example.com",
    role: "EDITOR",
    status: "PENDING",
    joinedAt: null,
  },
  {
    id: "user-005",
    name: null,
    email: "pending2@example.com",
    role: "VIEWER",
    status: "PENDING",
    joinedAt: null,
  },
];
