import axiosInstance from "./axiosInstance";

// Upload file PDF, TXT hoặc DOCX
export const uploadDocument = async (file, widgetConfigId) => {
  if (!widgetConfigId) {
    throw new Error("Thiếu widgetConfigId.");
  }
  const formData = new FormData();
  formData.append("file", file);

  const response = await axiosInstance.post(`/api/documents/upload/${widgetConfigId}`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
};

// Lấy danh sách tài liệu đã upload
export const getDocuments = async () => {
  const response = await axiosInstance.get("/api/documents");
  return response.data;
};