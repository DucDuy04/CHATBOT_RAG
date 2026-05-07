import StatusBadge from "../../../components/common/StatusBadge";
import SkeletonLoader from "../../../components/common/SkeletonLoader";
import EmptyState from "../../../components/common/EmptyState";

/** Format bytes to human-readable */
function fmtSize(bytes) {
  if (!bytes) return "0 B";
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  if (bytes >= 1024) return (bytes / 1024).toFixed(0) + " KB";
  return bytes + " B";
}

/** Format ISO date to "May 5, 2026" */
function fmtDate(iso) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleDateString("en-US", {
      month: "short", day: "numeric", year: "numeric",
    });
  } catch {
    return iso;
  }
}

/** Map document status to StatusBadge variant */
function statusVariant(status) {
  switch ((status || "").toUpperCase()) {
    case "INDEXED":    return "synced";
    case "PROCESSING": return "processing";
    case "FAILED":     return "failed";
    default:           return "info";
  }
}

/** Type badge */
function TypeBadge({ type }) {
  const colors = {
    PDF:  "bg-red-50 text-red-700 border-red-200",
    TXT:  "bg-gray-50 text-gray-600 border-gray-200",
    DOCX: "bg-blue-50 text-blue-700 border-blue-200",
  };
  const cls = colors[type] || "bg-gray-50 text-gray-500 border-gray-200";
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${cls}`}>
      {type || "—"}
    </span>
  );
}

/**
 * DocumentsTable — shows list of documents with actions.
 *
 * Props:
 *   documents      : array
 *   loading        : boolean
 *   error          : string | null
 *   onRetryLoad    : () => void
 *   onChunks       : (doc) => void
 *   onAssign       : (doc) => void
 *   onRetry        : (doc) => void
 *   onDelete       : (doc) => void
 *   actionLoading  : { [docId]: string }   — action key per doc
 */
export default function DocumentsTable({
  documents,
  loading,
  error,
  onRetryLoad,
  onChunks,
  onAssign,
  onRetry,
  onDelete,
  actionLoading = {},
}) {
  if (loading) {
    return <SkeletonLoader variant="table" count={6} />;
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center space-y-4">
        <p className="text-lg">⚠️</p>
        <p className="text-sm font-semibold text-red-700">Failed to load documents</p>
        <p className="text-sm text-red-500">{error}</p>
        <button
          onClick={onRetryLoad}
          className="px-4 py-2 text-sm font-medium bg-red-600 text-white rounded-lg hover:bg-red-700"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!documents || documents.length === 0) {
    return (
      <EmptyState
        icon="📄"
        title="No documents found"
        message="Upload a document or adjust your filters."
      />
    );
  }

  return (
    /* Horizontal scroll on small screens */
    <div className="overflow-x-auto rounded-xl border border-gray-200">
      <table className="min-w-full divide-y divide-gray-100 text-sm">
        <thead className="bg-gray-50">
          <tr>
            {["Filename", "Type", "Chatbot", "Status", "Chunks", "Size", "Uploaded", "Actions"].map(
              (col) => (
                <th
                  key={col}
                  className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap"
                >
                  {col}
                </th>
              )
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 bg-white">
          {documents.map((doc) => {
            const busy = actionLoading[doc.id];
            return (
              <tr key={doc.id} className="hover:bg-gray-50 transition-colors">
                {/* Filename */}
                <td className="px-4 py-3 max-w-[200px]">
                  <p className="font-medium text-gray-800 truncate" title={doc.filename}>
                    {doc.filename}
                  </p>
                  {doc.status === "FAILED" && doc.error && (
                    <p className="text-xs text-red-500 truncate mt-0.5" title={doc.error}>
                      {doc.error}
                    </p>
                  )}
                  {doc.status === "PROCESSING" && (
                    <div className="mt-1 h-1 rounded-full bg-gray-200 overflow-hidden w-24">
                      <div
                        className="h-full bg-blue-500 rounded-full transition-all"
                        style={{ width: `${doc.progress || 0}%` }}
                      />
                    </div>
                  )}
                </td>

                {/* Type */}
                <td className="px-4 py-3 whitespace-nowrap">
                  <TypeBadge type={doc.type} />
                </td>

                {/* Chatbot */}
                <td className="px-4 py-3 whitespace-nowrap text-gray-600">
                  {doc.chatbotName || (
                    <span className="text-gray-400 italic">Unassigned</span>
                  )}
                </td>

                {/* Status */}
                <td className="px-4 py-3 whitespace-nowrap">
                  <StatusBadge status={statusVariant(doc.status)} />
                </td>

                {/* Chunks */}
                <td className="px-4 py-3 whitespace-nowrap text-gray-600 tabular-nums">
                  {doc.chunkCount ?? "—"}
                </td>

                {/* Size */}
                <td className="px-4 py-3 whitespace-nowrap text-gray-600 tabular-nums">
                  {fmtSize(doc.sizeBytes)}
                </td>

                {/* Uploaded */}
                <td className="px-4 py-3 whitespace-nowrap text-gray-500">
                  {fmtDate(doc.uploadedAt)}
                </td>

                {/* Actions */}
                <td className="px-4 py-3 whitespace-nowrap">
                  <div className="flex items-center gap-1.5">
                    {/* Chunks */}
                    <ActionBtn
                      label="Chunks"
                      title="View chunks"
                      onClick={() => onChunks(doc)}
                      disabled={!!busy || doc.status !== "INDEXED"}
                    />

                    {/* Assign */}
                    <ActionBtn
                      label="Assign"
                      title="Assign to chatbot"
                      onClick={() => onAssign(doc)}
                      disabled={!!busy}
                      color="blue"
                    />

                    {/* Retry — only for FAILED */}
                    {doc.status === "FAILED" && (
                      <ActionBtn
                        label={busy === "retry" ? "…" : "Retry"}
                        title="Retry processing"
                        onClick={() => onRetry(doc)}
                        disabled={!!busy}
                        color="amber"
                      />
                    )}

                    {/* Delete */}
                    <ActionBtn
                      label={busy === "delete" ? "…" : "Delete"}
                      title="Delete document"
                      onClick={() => onDelete(doc)}
                      disabled={!!busy}
                      color="red"
                    />
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ActionBtn({ label, title, onClick, disabled, color = "gray" }) {
  const colors = {
    gray:  "text-gray-600 hover:bg-gray-100 border-gray-200",
    blue:  "text-blue-600 hover:bg-blue-50 border-blue-200",
    amber: "text-amber-600 hover:bg-amber-50 border-amber-200",
    red:   "text-red-600 hover:bg-red-50 border-red-200",
  };
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`px-2.5 py-1 text-xs font-medium rounded border transition-colors
                  ${colors[color] || colors.gray}
                  disabled:opacity-40 disabled:cursor-not-allowed`}
    >
      {label}
    </button>
  );
}
