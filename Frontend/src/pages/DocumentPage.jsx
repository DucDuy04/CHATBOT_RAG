import { useState, useEffect } from "react";
import { uploadDocument, getDocuments } from "../api/documentApi";

export default function DocumentPage() {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage]     = useState("");

  useEffect(() => {
    loadDocuments();
  }, []);

  const loadDocuments = async () => {
    try {
      const docs = await getDocuments();
      setDocuments(Array.isArray(docs) ? docs : []);
    } catch (error) {
      console.error("Không load được danh sách tài liệu:", error);
    }
  };

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    setMessage("");

    try {
      const result = await uploadDocument(file);
      setMessage(result.message);
      await loadDocuments();
    } catch (error) {
      console.error("Lỗi upload:", error);
      setMessage("Upload thất bại, vui lòng thử lại.");
    } finally {
      setUploading(false);
      e.target.value = ""; // Reset input để upload lại cùng file
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
            <p className="mt-1">Hỗ trợ PDF, TXT — tối đa 50MB</p>
          </>
        )}
        <input
          type="file"
          accept=".pdf,.txt"
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