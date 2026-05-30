import axiosInstance from "./axiosInstance";

export const settingsApi = {
  /** GET /api/settings/profile */
  getProfile: async () => {
    const res = await axiosInstance.get("/api/settings/profile");
    return res.data;
  },

  /** PUT /api/settings/profile */
  updateProfile: async (payload) => {
    const res = await axiosInstance.put("/api/settings/profile", payload);
    return res.data;
  },

  /** GET /api/settings/api-keys */
  getApiKeys: async () => {
    const res = await axiosInstance.get("/api/settings/api-keys");
    return res.data;
  },

  /**
   * POST /api/settings/api-keys
   * Returns { key (record), plainTextKey (shown once) }
   */
  generateApiKey: async ({ name }) => {
    const res = await axiosInstance.post("/api/settings/api-keys", { name });
    return res.data;
  },

  /** DELETE /api/settings/api-keys/:id */
  deleteApiKey: async (id) => {
    const res = await axiosInstance.delete(`/api/settings/api-keys/${id}`);
    return res.data;
  },
};
