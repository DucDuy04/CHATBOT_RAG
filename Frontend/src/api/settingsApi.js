import axiosInstance from "./axiosInstance";
import { USE_MOCK_API, mockDelay } from "./apiMode";
import { profile, apiKeys, teamMembers } from "../mocks/settingsMock";

let _nextKeyId = 4;
let _nextUserId = 6;

/** Fake plain-text key — shown only once */
const genFakePlainKey = () => {
  const chars = "abcdefghijklmnopqrstuvwxyz0123456789";
  const rand = (n) => Array.from({ length: n }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
  return `sk_live_${rand(8)}${rand(4)}${rand(4)}`;
};

export const settingsApi = {
  /** GET /api/settings/profile */
  getProfile: async () => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return { ...profile };
    }
    const res = await axiosInstance.get("/api/settings/profile");
    return res.data;
  },

  /** PUT /api/settings/profile */
  updateProfile: async (payload) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      Object.assign(profile, payload);
      return { ...profile };
    }
    const res = await axiosInstance.put("/api/settings/profile", payload);
    return res.data;
  },

  /** GET /api/settings/api-keys */
  getApiKeys: async () => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return apiKeys.map((k) => ({ ...k }));
    }
    const res = await axiosInstance.get("/api/settings/api-keys");
    return res.data;
  },

  /**
   * POST /api/settings/api-keys
   * Returns { key (record), plainTextKey (shown once) }
   */
  generateApiKey: async ({ name }) => {
    if (USE_MOCK_API) {
      await mockDelay(500);
      const plain = genFakePlainKey();
      const suffix = plain.slice(-4);
      const newKey = {
        id: `key-00${_nextKeyId++}`,
        name: name || "New API Key",
        maskedKey: `sk_live_****${suffix}`,
        status: "ACTIVE",
        lastUsedAt: null,
        createdAt: new Date().toISOString(),
      };
      apiKeys.push(newKey);
      return { key: { ...newKey }, plainTextKey: plain };
    }
    const res = await axiosInstance.post("/api/settings/api-keys", { name });
    return res.data;
  },

  /** DELETE /api/settings/api-keys/:id */
  deleteApiKey: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      const idx = apiKeys.findIndex((k) => k.id === id);
      if (idx >= 0) apiKeys.splice(idx, 1);
      return { success: true };
    }
    const res = await axiosInstance.delete(`/api/settings/api-keys/${id}`);
    return res.data;
  },

  /** GET /api/settings/team */
  getTeam: async () => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      return teamMembers.map((m) => ({ ...m }));
    }
    const res = await axiosInstance.get("/api/settings/team");
    return res.data;
  },

  /** POST /api/settings/team/invite — body: { email, role } */
  inviteMember: async ({ email, role }) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      const existing = teamMembers.find((m) => m.email === email);
      if (existing) throw { message: "Email đã tồn tại trong nhóm.", status: 409 };
      const newMember = {
        id: `user-00${_nextUserId++}`,
        name: null,
        email,
        role,
        status: "PENDING",
        joinedAt: null,
      };
      teamMembers.push(newMember);
      return { ...newMember };
    }
    const res = await axiosInstance.post("/api/settings/team/invite", { email, role });
    return res.data;
  },

  /** PUT /api/settings/team/:userId/role */
  updateMemberRole: async (userId, role) => {
    if (USE_MOCK_API) {
      await mockDelay(350);
      const member = teamMembers.find((m) => m.id === userId);
      if (!member) throw { message: "Thành viên không tồn tại.", status: 404 };
      member.role = role;
      return { ...member };
    }
    const res = await axiosInstance.put(`/api/settings/team/${userId}/role`, { role });
    return res.data;
  },

  /** DELETE /api/settings/team/:userId */
  removeMember: async (userId) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      const idx = teamMembers.findIndex((m) => m.id === userId);
      if (idx >= 0) teamMembers.splice(idx, 1);
      return { success: true };
    }
    const res = await axiosInstance.delete(`/api/settings/team/${userId}`);
    return res.data;
  },
};
