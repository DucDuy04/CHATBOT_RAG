import { useRef, useState } from "react";
import { useToast } from "../../../components/common/useToast";

const ALLOWED_EXTENSIONS = ["pdf", "txt", "docx"];
const ALLOWED_MIME = [
  "application/pdf",
  "text/plain",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
];
const MAX_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

/** Format bytes to KB/MB */
function fmtSize(bytes) {
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + " KB";
  return bytes + " B";
}

/** Validate a File; return null if ok, string error if invalid. */
function validateFile(file) {
  const ext = file.name.split(".").pop()?.toLowerCase() || "";
  const mimeOk = ALLOWED_MIME.includes(file.type) || ALLOWED_EXTENSIONS.includes(ext);
  if (!mimeOk) {
    return `"${file.name}" — unsupported type. Allowed: PDF, TXT, DOCX.`;
  }
  if (file.size > MAX_SIZE_BYTES) {
    return `"${file.name}" — exceeds 50 MB limit (${fmtSize(file.size)}).`;
  }
  return null;
}

/**
 * UploadZone — drag/drop or click to upload documents.
 *
 * Props:
 *   onUpload : (files: File[]) => Promise<void>   — called with valid files
 *   disabled : boolean
 *   chatbots : { id, name }[]
 *   selectedChatbotId : string
 *   onChatbotChange : (id: string) => void
 */
export default function UploadZone({
  onUpload,
  disabled = false,
  chatbots = [],
  selectedChatbotId = "",
  onChatbotChange,
}) {
  const toast = useToast();
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const [fileErrors, setFileErrors] = useState([]);
  const [uploadingFiles, setUploadingFiles] = useState([]); // [{name, size}]

  const uploadBlocked =
    disabled || !selectedChatbotId || String(selectedChatbotId).trim() === "";

  function handleFiles(files) {
    if (uploadBlocked) {
      toast.warning("Chọn chatbot trước khi upload tài liệu.");
      return;
    }

    setFileErrors([]);
    const valid = [];
    const errors = [];

    for (const file of files) {
      const err = validateFile(file);
      if (err) errors.push(err);
      else valid.push(file);
    }

    if (errors.length) setFileErrors(errors);
    if (!valid.length) return;

    setUploadingFiles(valid.map((f) => ({ name: f.name, size: f.size })));

    onUpload(valid).finally(() => {
      setUploadingFiles([]);
    });
  }

  function handleDrop(e) {
    e.preventDefault();
    setDragging(false);
    if (uploadBlocked) {
      toast.warning("Chọn chatbot trước khi upload tài liệu.");
      return;
    }
    const files = Array.from(e.dataTransfer.files);
    handleFiles(files);
  }

  function handleDragOver(e) {
    e.preventDefault();
    if (!uploadBlocked) setDragging(true);
  }

  function handleDragLeave() {
    setDragging(false);
  }

  function handleInputChange(e) {
    const files = Array.from(e.target.files || []);
    if (files.length) handleFiles(files);
    // Reset input so the same file can be re-selected
    e.target.value = "";
  }

  const isUploading = uploadingFiles.length > 0;
  const zoneInactive = uploadBlocked || isUploading;

  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <label className="text-sm font-medium text-gray-700" htmlFor="upload-chatbot-select">
          Chọn chatbot để upload
        </label>
        <select
          id="upload-chatbot-select"
          value={selectedChatbotId}
          onChange={(e) => onChatbotChange?.(e.target.value)}
          disabled={disabled}
          className="w-full max-w-md rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 disabled:opacity-60"
        >
          <option value="">Select chatbot</option>
          {chatbots.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name || c.id}
            </option>
          ))}
        </select>
      </div>

      {/* Drop zone */}
      <div
        role="button"
        tabIndex={zoneInactive ? -1 : 0}
        aria-label="Upload documents — click or drag and drop"
        aria-disabled={zoneInactive}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onClick={() => !zoneInactive && inputRef.current?.click()}
        onKeyDown={(e) =>
          e.key === "Enter" && !zoneInactive && inputRef.current?.click()
        }
        className={`
          relative rounded-xl border-2 border-dashed p-6 text-center select-none
          transition-colors
          ${uploadBlocked
            ? "border-amber-300 bg-amber-50/50 cursor-not-allowed opacity-80"
            : "cursor-pointer border-gray-300 bg-gray-50 hover:border-blue-400 hover:bg-blue-50/40"}
          ${dragging && !uploadBlocked
            ? "border-blue-500 bg-blue-50"
            : ""}
          ${isUploading ? "opacity-60 cursor-wait pointer-events-none" : ""}
        `}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept=".pdf,.txt,.docx,application/pdf,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          className="hidden"
          onChange={handleInputChange}
          disabled={zoneInactive}
        />

        {isUploading ? (
          <div className="space-y-2">
            <p className="text-sm font-medium text-blue-600">Uploading…</p>
            <ul className="space-y-1">
              {uploadingFiles.map((f) => (
                <li key={f.name} className="flex items-center justify-between text-xs text-gray-600 gap-2">
                  <span className="truncate max-w-[200px]">{f.name}</span>
                  <span className="flex-shrink-0 flex items-center gap-1 text-blue-500">
                    <span className="w-3 h-3 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                    {fmtSize(f.size)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <>
            <div className="text-3xl mb-2">📂</div>
            <p className="text-sm font-medium text-gray-700">
              {uploadBlocked ? (
                <span className="text-amber-800">
                  Chọn chatbot phía trên để bật upload.
                </span>
              ) : (
                <>
                  Drop files here, or{" "}
                  <span className="text-blue-600 underline">click to browse</span>
                </>
              )}
            </p>
            <p className="text-xs text-gray-400 mt-1">
              PDF, TXT, DOCX — max 50 MB per file
            </p>
          </>
        )}
      </div>

      {/* Validation errors */}
      {fileErrors.length > 0 && (
        <ul className="space-y-1">
          {fileErrors.map((err, i) => (
            <li key={i} className="text-xs text-red-600 flex items-start gap-1">
              <span>⚠</span>
              <span>{err}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
