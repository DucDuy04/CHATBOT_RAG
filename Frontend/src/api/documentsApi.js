import axiosInstance from "./axiosInstance";

/** UUID string shape — only send chatbotId filter when valid (avoid junk query params) */
const CHATBOT_ID_PARAM_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const STATUS_SENTINELS = new Set(["all statuses", "all status"]);

/** POST /api/documents/upload — FormData: chatbotId + files[] */
function buildUploadFormData(files, chatbotId) {
  const fd = new FormData();
  fd.append("chatbotId", String(chatbotId));
  const list = Array.isArray(files) ? files : [files];
  list.forEach((f) => {
    if (f instanceof File) fd.append("files", f);
  });
  return fd;
}

export const documentsApi = {
  /**
   * POST /api/documents/upload — multipart/form-data
   * @param {File[]|File|FormData} filesOrFormData — thường là File[]; FormData (ít dùng) sẽ được merge chatbotId
   * @param {string} chatbotId — UUID chatbot/widget
   */
  uploadDocuments: async (filesOrFormData, chatbotId) => {
    if (chatbotId == null || String(chatbotId).trim() === "") {
      throw new Error("chatbotId is required for document upload");
    }
    const cid = String(chatbotId).trim();

    let fd;
    if (filesOrFormData instanceof FormData) {
      fd = filesOrFormData;
      fd.set("chatbotId", cid);
    } else {
      fd = buildUploadFormData(filesOrFormData, cid);
    }
    const res = await axiosInstance.post("/api/documents/upload", fd, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return res.data;
  },

  /** GET /api/documents?search=&type=&chatbotId=&status=&page=&size= */
  getDocuments: async ({ search = "", type = "", chatbotId = "", status = "", page = 0, size = 10 } = {}) => {
    // Omit empty filter params — avoids brittle server parsing and keeps URLs clean.
    const params = { page, size };
    const s = search != null ? String(search).trim() : "";
    const t = type != null ? String(type).trim() : "";
    const cid = chatbotId != null ? String(chatbotId).trim() : "";
    let st = status != null ? String(status).trim() : "";
    if (STATUS_SENTINELS.has(st.toLowerCase())) {
      st = "";
    }
    if (s) params.search = s;
    if (t) params.type = t;
    if (cid && CHATBOT_ID_PARAM_RE.test(cid)) params.chatbotId = cid;
    if (st) params.status = st;
    const res = await axiosInstance.get("/api/documents", { params });
    return res.data;
  },

  /** GET /api/documents/:id/status */
  getDocumentStatus: async (id) => {
    const res = await axiosInstance.get(`/api/documents/${id}/status`);
    return res.data;
  },

  /** GET /api/documents/:id/chunks */
  getDocumentChunks: async (id) => {
    const res = await axiosInstance.get(`/api/documents/${id}/chunks`);
    return res.data;
  },

  /** POST /api/documents/:id/assign — body: { chatbotId } */
  assignDocument: async (id, { chatbotId: targetChatbotId }) => {
    const res = await axiosInstance.post(`/api/documents/${id}/assign`, { chatbotId: targetChatbotId });
    return res.data;
  },

  /** POST /api/documents/:id/retry */
  retryDocument: async (id) => {
    const res = await axiosInstance.post(`/api/documents/${id}/retry`);
    return res.data;
  },

  /** DELETE /api/documents/:id */
  deleteDocument: async (id) => {
    const res = await axiosInstance.delete(`/api/documents/${id}`);
    return res.data;
  },
};
