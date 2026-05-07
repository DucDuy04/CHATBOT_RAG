import { useRef, useState } from "react";

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
 */
export default function UploadZone({ onUpload, disabled = false }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const [fileErrors, setFileErrors] = useState([]);
  const [uploadingFiles, setUploadingFiles] = useState([]); // [{name, size}]

  function handleFiles(files) {
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
    if (disabled) return;
    const files = Array.from(e.dataTransfer.files);
    handleFiles(files);
  }

  function handleDragOver(e) {
    e.preventDefault();
    if (!disabled) setDragging(true);
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

  return (
    <div className="space-y-2">
      {/* Drop zone */}
      <div
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-label="Upload documents — click or drag and drop"
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onClick={() => !disabled && !isUploading && inputRef.current?.click()}
        onKeyDown={(e) => e.key === "Enter" && !disabled && !isUploading && inputRef.current?.click()}
        className={`
          relative rounded-xl border-2 border-dashed p-6 text-center cursor-pointer
          transition-colors select-none
          ${dragging
            ? "border-blue-500 bg-blue-50"
            : "border-gray-300 bg-gray-50 hover:border-blue-400 hover:bg-blue-50/40"}
          ${(disabled || isUploading) ? "opacity-60 cursor-not-allowed pointer-events-none" : ""}
        `}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept=".pdf,.txt,.docx,application/pdf,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          className="hidden"
          onChange={handleInputChange}
          disabled={disabled || isUploading}
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
              Drop files here, or{" "}
              <span className="text-blue-600 underline">click to browse</span>
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
