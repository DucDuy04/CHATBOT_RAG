import { useState, useEffect } from "react";
import Drawer from "../../../components/common/Drawer";
import SkeletonLoader from "../../../components/common/SkeletonLoader";
import EmptyState from "../../../components/common/EmptyState";
import { documentsApi } from "../../../api";

/**
 * ChunkDrawer — shows chunks for a single document.
 *
 * Props:
 *   document : { id, filename } | null
 *   onClose  : () => void
 *
 * Note: all setState calls are inside Promise callbacks (async) to satisfy
 * react-hooks/set-state-in-effect rule from eslint-plugin-react-hooks v7.
 */
export default function ChunkDrawer({ document, onClose }) {
  const isOpen = !!document;
  const [chunks,  setChunks]  = useState([]);
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState(null);

  useEffect(() => {
    if (!document) return;
    const docId = document.id;

    // All setState calls inside async Promise chain — avoids react-hooks/set-state-in-effect
    Promise.resolve()
      .then(() => {
        setLoading(true);
        setError(null);
        setChunks([]);
        return documentsApi.getDocumentChunks(docId);
      })
      .then((data) => {
        setChunks(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        setError(err?.message || "Failed to load chunks.");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [document]);

  function handleRetry() {
    if (!document) return;
    setLoading(true);
    setError(null);
    documentsApi.getDocumentChunks(document.id)
      .then((data) => setChunks(Array.isArray(data) ? data : []))
      .catch((err) => setError(err?.message || "Failed to load chunks."))
      .finally(() => setLoading(false));
  }

  return (
    <Drawer
      isOpen={isOpen}
      onClose={onClose}
      title={document ? `Chunks — ${document.filename}` : "Chunks"}
      width="w-[480px] max-w-full"
    >
      {loading && <SkeletonLoader variant="line" count={8} />}

      {!loading && error && (
        <div className="space-y-3 text-center py-8">
          <p className="text-sm text-red-600">⚠️ {error}</p>
          <button
            onClick={handleRetry}
            className="px-4 py-2 text-sm font-medium bg-red-600 text-white rounded-lg hover:bg-red-700"
          >
            Retry
          </button>
        </div>
      )}

      {!loading && !error && chunks.length === 0 && (
        <EmptyState
          icon="🧩"
          title="No chunks"
          message="This document has no indexed chunks yet."
        />
      )}

      {!loading && !error && chunks.length > 0 && (
        <div className="space-y-3">
          <p className="text-xs text-gray-400">
            {chunks.length} chunk{chunks.length !== 1 ? "s" : ""} total
          </p>
          {chunks.map((chunk, idx) => (
            <div
              key={chunk.chunkIndex ?? idx}
              className="rounded-lg border border-gray-200 bg-gray-50 p-3 space-y-1"
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-gray-500">
                  Chunk #{(chunk.chunkIndex ?? idx) + 1}
                </span>
                {chunk.tokenCount != null && (
                  <span className="text-xs text-gray-400">{chunk.tokenCount} tokens</span>
                )}
              </div>
              <p className="text-xs text-gray-700 leading-relaxed break-words">
                {chunk.content || "—"}
              </p>
            </div>
          ))}
        </div>
      )}
    </Drawer>
  );
}
