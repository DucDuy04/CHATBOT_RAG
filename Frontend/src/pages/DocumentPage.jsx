import { useState, useEffect } from "react";
import { uploadDocument, getDocuments } from "../api/documentApi";
import { createWidget } from "../api/widgetApi";

export default function DocumentPage() {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [creatingWidget, setCreatingWidget] = useState(false);
  const [message, setMessage]     = useState("");
  const [widgetConfigId, setWidgetConfigId] = useState(
    localStorage.getItem("widget_config_id") || ""
  );
  const [widgetApiKey, setWidgetApiKey] = useState(
    localStorage.getItem("widget_api_key") || ""
  );

  useEffect(() => {
    loadDocuments();
  }, []);

  const loadDocuments = async () => {
    try {
      const docs = await getDocuments();
      setDocuments(docs);
    } catch (error) {
      console.error("Không load được danh sách tài liệu:", error);
    }
  };

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!widgetConfigId) {
      setMessage("Thiếu widgetConfigId. Hãy nhập và lưu cấu hình widget trước khi upload.");
      e.target.value = "";
      return;
    }

    setUploading(true);
    setMessage("");

    try {
      const result = await uploadDocument(file, widgetConfigId);
      setMessage(result.message);
      await loadDocuments();
    } catch (error) {
      console.error("Lỗi upload:", error);
      setMessage(error?.response?.data?.message || error?.message || "Upload thất bại, vui lòng thử lại.");
    } finally {
      setUploading(false);
      e.target.value = ""; // Reset input để upload lại cùng file
    }
  };

  const handleSaveWidgetConfig = () => {
    const normalizedWidgetId = widgetConfigId.trim();
    const normalizedApiKey = widgetApiKey.trim();

    if (!normalizedWidgetId || !normalizedApiKey) {
      setMessage("Cần nhập đủ widgetConfigId và widgetApiKey.");
      return;
    }

    localStorage.setItem("widget_config_id", normalizedWidgetId);
    localStorage.setItem("widget_api_key", normalizedApiKey);
    setWidgetConfigId(normalizedWidgetId);
    setWidgetApiKey(normalizedApiKey);
    setMessage("Đã lưu widget config vào localStorage.");
  };

  const handleCreateWidget = async () => {
    setCreatingWidget(true);
    setMessage("");

    try {
      const result = await createWidget({
        name: `Local widget ${new Date().toISOString()}`,
        allowedOrigin: [window.location.origin],
        uiConfig: {},
      });

      const newWidgetConfigId = result.widgetConfigId || "";
      const newWidgetApiKey = result.apiKey || "";

      localStorage.setItem("widget_config_id", newWidgetConfigId);
      localStorage.setItem("widget_api_key", newWidgetApiKey);
      setWidgetConfigId(newWidgetConfigId);
      setWidgetApiKey(newWidgetApiKey);
      setMessage("Da tao widget moi va luu cau hinh. Ban co the upload tai lieu va chat ngay.");
    } catch (error) {
      console.error("Khong tao duoc widget:", error);
      setMessage(error?.response?.data?.message || error?.message || "Tao widget that bai, vui long thu lai.");
    } finally {
      setCreatingWidget(false);
    }
  };

  const statusColor = (status) => {
    switch (status) {
      case "COMPLETED":  return "text-green-600 bg-green-50";
      case "PROCESSING": return "text-yellow-600 bg-yellow-50";
      case "FAILED":     return "text-red-600 bg-red-50";
      default:           return "text-gray-600 bg-gray-50";
    }
  };

  const formatSize = (bytes) => {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h1 className="text-xl font-semibold mb-6">Quản lý tài liệu</h1>

      <div className="mb-6 p-4 border rounded-xl bg-white space-y-3">
        <p className="text-sm font-medium text-gray-700">Cấu hình Widget để upload/chat</p>
        <input
          type="text"
          value={widgetConfigId}
          onChange={(e) => setWidgetConfigId(e.target.value)}
          placeholder="widgetConfigId (UUID)"
          className="w-full px-3 py-2 text-sm border rounded-lg outline-none focus:ring-2 focus:ring-blue-500"
        />
        <input
          type="text"
          value={widgetApiKey}
          onChange={(e) => setWidgetApiKey(e.target.value)}
          placeholder="widgetApiKey (UUID)"
          className="w-full px-3 py-2 text-sm border rounded-lg outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          onClick={handleSaveWidgetConfig}
          className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700"
        >
          Lưu cấu hình widget
        </button>
        <button
          type="button"
          onClick={handleCreateWidget}
          disabled={creatingWidget}
          className="ml-2 px-4 py-2 text-sm font-medium text-blue-700 bg-blue-50 rounded-lg hover:bg-blue-100 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {creatingWidget ? "Dang tao widget..." : "Tao widget moi"}
        </button>
      </div>

      {/* Upload box */}
      <label
        className={`flex flex-col items-center justify-center border-2
                    border-dashed rounded-xl p-10 cursor-pointer
                    transition-colors text-sm text-gray-500
                    ${uploading
                      ? "border-gray-200 bg-gray-50 cursor-not-allowed"
                      : "border-gray-300 hover:border-blue-400 hover:bg-blue-50"}`}
      >
        {uploading ? (
          <p className="text-blue-600">Đang xử lý file...</p>
        ) : (
          <>
            <p className="font-medium text-gray-700">Click để chọn file</p>
            <p className="mt-1">Hỗ trợ PDF, TXT, DOCX — tối đa 50MB</p>
          </>
        )}
        <input
          type="file"
          accept=".pdf,.txt,.docx,application/pdf,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          onChange={handleFileChange}
          disabled={uploading}
          className="hidden"
        />
      </label>

      {/* Thông báo kết quả */}
      {message && (
        <p
          className={`mt-3 text-sm px-4 py-2 rounded-lg
            ${message.includes("thất bại")
              ? "bg-red-50 text-red-600"
              : "bg-green-50 text-green-600"}`}
        >
          {message}
        </p>
      )}

      {/* Danh sách tài liệu */}
      <div className="mt-6 space-y-2">
        <h2 className="font-medium text-gray-700 mb-3">
          Tài liệu đã upload ({documents.length})
        </h2>

        {documents.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-8">
            Chưa có tài liệu nào
          </p>
        ) : (
          documents.map((doc) => (
            <div
              key={doc.id}
              className="flex items-center justify-between bg-white
                         border rounded-xl px-4 py-3 text-sm"
            >
              <div>
                <p className="font-medium text-gray-800">{doc.fileName}</p>
                <p className="text-gray-400 text-xs mt-0.5">
                  {formatSize(doc.fileSize)}
                  {doc.chunkCount ? ` · ${doc.chunkCount} chunks` : ""}
                </p>
              </div>
              <span
                className={`px-3 py-1 rounded-full text-xs font-medium
                  ${statusColor(doc.status)}`}
              >
                {doc.status}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
