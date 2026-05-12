import axiosInstance from "./axiosInstance";
import { USE_MOCK_API, mockDelay, createPaginatedResponse } from "./apiMode";
import { documents, chunksByDoc } from "../mocks/documentsMock";
import { chatbots } from "../mocks/chatbotsMock";

let _nextDocId = 11;

/** UUID string shape — only send chatbotId filter when valid (avoid junk query params) */
const CHATBOT_ID_PARAM_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const STATUS_SENTINELS = new Set(["all statuses", "all status"]);

/** Chuẩn hoá File[] từ tham số upload */
function normalizeFiles(filesOrFormData) {
  if (filesOrFormData instanceof FormData) {
    const out = [];
    for (const [, v] of filesOrFormData.entries()) {
      if (v instanceof File) out.push(v);
    }
    return out;
  }
  return Array.isArray(filesOrFormData) ? filesOrFormData : [filesOrFormData];
}

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
   * @param {string} chatbotId — UUID chatbot/widget (bắt buộc khi real API và mock)
   */
  uploadDocuments: async (filesOrFormData, chatbotId) => {
    if (chatbotId == null || String(chatbotId).trim() === "") {
      throw new Error("chatbotId is required for document upload");
    }
    const cid = String(chatbotId).trim();

    if (USE_MOCK_API) {
      await mockDelay(600);
      const files = normalizeFiles(filesOrFormData);
      const uploaded = [];
      const bot = chatbots.find((c) => c.id === cid);
      for (const file of files) {
        if (!(file instanceof File)) continue;
        const ext = file.name.split(".").pop().toUpperCase();
        const newDoc = {
          id: `doc-0${_nextDocId++}`,
          filename: file.name,
          type: ["PDF", "TXT"].includes(ext) ? ext : "OTHER",
          chatbotId: cid,
          chatbotName: bot ? bot.name : null,
          chunkCount: 0,
          sizeBytes: file.size,
          status: "PROCESSING",
          progress: 0,
          uploadedAt: new Date().toISOString(),
          error: null,
        };
        documents.push(newDoc);
        uploaded.push(newDoc);
      }
      return uploaded;
    }

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
    if (USE_MOCK_API) {
      await mockDelay(350);
      let filtered = [...documents];
      if (search) {
        const q = search.toLowerCase();
        filtered = filtered.filter((d) => d.filename.toLowerCase().includes(q));
      }
      if (type)       filtered = filtered.filter((d) => d.type === type);
      if (chatbotId)  filtered = filtered.filter((d) => d.chatbotId === chatbotId);
      if (status)     filtered = filtered.filter((d) => d.status === status);
      return createPaginatedResponse(filtered, page, size);
    }
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
    if (USE_MOCK_API) {
      await mockDelay(200);
      const doc = documents.find((d) => d.id === id);
      if (!doc) throw { message: "Document không tồn tại.", status: 404 };
      // Simulate incremental progress for PROCESSING
      if (doc.status === "PROCESSING") {
        doc.progress = Math.min((doc.progress || 0) + 15, 100);
        if (doc.progress >= 100) {
          doc.status = "INDEXED";
          doc.chunkCount = Math.floor(Math.random() * 60) + 10;
        }
      }
      return { id: doc.id, status: doc.status, progress: doc.progress, error: doc.error };
    }
    const res = await axiosInstance.get(`/api/documents/${id}/status`);
    return res.data;
  },

  /** GET /api/documents/:id/chunks */
  getDocumentChunks: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      const chunks = chunksByDoc[id];
      if (!chunks) return [];
      return chunks;
    }
    const res = await axiosInstance.get(`/api/documents/${id}/chunks`);
    return res.data;
  },

  /** POST /api/documents/:id/assign — body: { chatbotId } */
  assignDocument: async (id, { chatbotId: targetChatbotId }) => {
    if (USE_MOCK_API) {
      await mockDelay(400);
      const doc = documents.find((d) => d.id === id);
      if (!doc) throw { message: "Document không tồn tại.", status: 404 };
      const bot = chatbots.find((c) => c.id === targetChatbotId);
      doc.chatbotId = targetChatbotId;
      doc.chatbotName = bot ? bot.name : targetChatbotId;
      return { ...doc };
    }
    const res = await axiosInstance.post(`/api/documents/${id}/assign`, { chatbotId: targetChatbotId });
    return res.data;
  },

  /** POST /api/documents/:id/retry */
  retryDocument: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      const doc = documents.find((d) => d.id === id);
      if (!doc) throw { message: "Document không tồn tại.", status: 404 };
      if (doc.status !== "FAILED") throw { message: "Chỉ có thể retry document đang FAILED.", status: 400 };
      doc.status = "PROCESSING";
      doc.progress = 0;
      doc.error = null;
      return { success: true };
    }
    const res = await axiosInstance.post(`/api/documents/${id}/retry`);
    return res.data;
  },

  /** DELETE /api/documents/:id */
  deleteDocument: async (id) => {
    if (USE_MOCK_API) {
      await mockDelay(300);
      const idx = documents.findIndex((d) => d.id === id);
      if (idx < 0) throw { message: "Document không tồn tại.", status: 404 };
      documents.splice(idx, 1);
      return { success: true };
    }
    const res = await axiosInstance.delete(`/api/documents/${id}`);
    return res.data;
  },
};
