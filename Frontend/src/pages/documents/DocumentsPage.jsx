import { useState, useEffect, useCallback, useRef } from "react";
import { documentsApi } from "../../api";
import { chatbotsApi } from "../../api";
import { useLayout } from "../../contexts/LayoutContext";
import { useToast } from "../../components/common/useToast";
import { ConfirmDeleteModal } from "../../components/common";

import UploadZone from "./components/UploadZone";
import DocumentsToolbar from "./components/DocumentsToolbar";
import DocumentsTable from "./components/DocumentsTable";
import DocumentsPagination from "./components/DocumentsPagination";
import ChunkDrawer from "./components/ChunkDrawer";
import AssignChatbotModal from "./components/AssignChatbotModal";

const POLL_INTERVAL_MS = 3000;
const DEFAULT_FILTERS = { search: "", type: "", chatbotId: "", status: "" };
const PAGE_SIZE = 10;

function getApiErrorMessage(err, fallback) {
  return (
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    err?.message ||
    fallback
  );
}

/**
 * DocumentsPage — /documents
 *
 * Manages document list, upload, polling, search/filter, actions.
 */
export default function DocumentsPage() {
  const { setRightSlot, clearRightSlot } = useLayout();
  const toast = useToast();

  // ─── Documents list state ──────────────────────────────────────────────────
  const [documents,  setDocuments]  = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [loadError,  setLoadError]  = useState(null);
  const [pagination, setPagination] = useState({ page: 0, size: PAGE_SIZE, total: 0, totalPages: 1 });

  // ─── Filters ───────────────────────────────────────────────────────────────
  // _searchRaw is uncontrolled display value; search is debounced API value
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [searchRaw, setSearchRaw] = useState("");

  // ─── Chatbots (for filter + assign modal) ─────────────────────────────────
  const [chatbots, setChatbots] = useState([]);

  // ─── Action loading map: { [docId]: "retry" | "delete" | "assign" } ───────
  const [actionLoading, setActionLoading] = useState({});

  // ─── Modals/Drawer ─────────────────────────────────────────────────────────
  const [chunkDoc,  setChunkDoc]  = useState(null); // doc for ChunkDrawer
  const [assignDoc, setAssignDoc] = useState(null); // doc for AssignChatbotModal
  const [deleteDoc, setDeleteDoc] = useState(null); // doc for ConfirmDeleteModal
  const [deleting,  setDeleting]  = useState(false);

  // ─── Polling ───────────────────────────────────────────────────────────────
  const pollRef = useRef(null);

  // ─── Upload target chatbot (tenant context — required by backend) ───────────
  const [uploadChatbotId, setUploadChatbotId] = useState("");

  // ─── Fetch documents ───────────────────────────────────────────────────────

  const fetchDocuments = useCallback(async (page = 0) => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await documentsApi.getDocuments({
        search:    filters.search,
        type:      filters.type,
        chatbotId: filters.chatbotId,
        status:    filters.status,
        page,
        size:      PAGE_SIZE,
      });
      const items = res.items || [];
      setDocuments(items);
      setPagination({ page: res.page, size: res.size, total: res.total, totalPages: res.totalPages });
      return { ...res, items };
    } catch (err) {
      setLoadError(getApiErrorMessage(err, "Failed to load documents."));
      return null;
    } finally {
      setLoading(false);
    }
  }, [filters]);

  // Initial load + re-fetch when filters change (reset page to 0)
  useEffect(() => {
    fetchDocuments(0);
  }, [fetchDocuments]);

  // ─── Load chatbots once for filters + assign modal ─────────────────────────

  useEffect(() => {
    chatbotsApi.getChatbots({ page: 0, size: 100 })
      .then((res) => setChatbots(res.items || []))
      .catch(() => {/* non-critical */});
  }, []);

  // ─── Polling: watch for PROCESSING documents ───────────────────────────────

  useEffect(() => {
    // Clear any existing interval first
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }

    const processingIds = documents
      .filter((d) => d.status === "PROCESSING")
      .map((d) => d.id);

    if (processingIds.length === 0) return;

    pollRef.current = setInterval(async () => {
      let anyStillProcessing = false;

      await Promise.allSettled(
        processingIds.map(async (id) => {
          try {
            const statusData = await documentsApi.getDocumentStatus(id);
            setDocuments((prev) =>
              prev.map((d) => {
                if (d.id !== id) return d;
                const updated = { ...d, status: statusData.status, progress: statusData.progress };
                if (statusData.status === "PROCESSING") anyStillProcessing = true;
                return updated;
              })
            );
            if (statusData.status === "PROCESSING") anyStillProcessing = true;
          } catch {
            // Non-fatal: keep polling for this doc
            anyStillProcessing = true;
            console.warn(`[poll] Failed to get status for ${id}`);
          }
        })
      );

      if (!anyStillProcessing) {
        clearInterval(pollRef.current);
        pollRef.current = null;
        // Refresh full list to get updated chunkCount, etc.
        fetchDocuments(pagination.page);
      }
    }, POLL_INTERVAL_MS);

    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documents, pagination.page]);

  // ─── Header rightSlot: Refresh button ─────────────────────────────────────

  useEffect(() => {
    setRightSlot(
      <button
        onClick={() => fetchDocuments(pagination.page)}
        disabled={loading}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium
                   border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50
                   disabled:opacity-60 transition-colors"
      >
        🔄 Refresh
      </button>
    );
  }, [fetchDocuments, loading, pagination.page, setRightSlot]);

  useEffect(() => {
    return () => clearRightSlot();
  }, [clearRightSlot]);

  // ─── Filter handlers ───────────────────────────────────────────────────────

  function handleFilter(key, value) {
    if (key === "_searchRaw") {
      setSearchRaw(value);
      return;
    }
    setFilters((f) => ({ ...f, [key]: value }));
  }

  function handleClearFilters() {
    setFilters(DEFAULT_FILTERS);
    setSearchRaw("");
  }

  // ─── Upload ────────────────────────────────────────────────────────────────

  async function handleUpload(files) {
    if (!uploadChatbotId || String(uploadChatbotId).trim() === "") {
      toast.warning("Chọn chatbot trước khi upload tài liệu.");
      return;
    }
    try {
      const uploaded = await documentsApi.uploadDocuments(files, uploadChatbotId.trim());
      const count = Array.isArray(uploaded) ? uploaded.length : 1;
      toast.success(`${count} file${count > 1 ? "s" : ""} uploaded — processing started.`);
      const refreshed = await fetchDocuments(0);
      const hasChatbotFilterMismatch = !!filters.chatbotId && filters.chatbotId !== uploadChatbotId.trim();
      if (hasChatbotFilterMismatch) {
        toast.info("Uploaded document co the khong hien do chatbot filter hien tai khac chatbot vua upload.");
      } else if (Array.isArray(uploaded) && Array.isArray(refreshed?.items)) {
        const uploadedIds = new Set(uploaded.map((d) => d?.id).filter(Boolean));
        if (uploadedIds.size > 0) {
          const visible = refreshed.items.some((d) => uploadedIds.has(d.id));
          if (!visible) {
            toast.info("Document da upload nhung khong nam trong filter/status hien tai.");
          }
        }
      }
    } catch (err) {
      const msg = getApiErrorMessage(err, "Upload failed.");
      toast.error(msg);
      throw err;
    }
  }

  // ─── Action helpers ────────────────────────────────────────────────────────

  function setDocAction(id, action) {
    setActionLoading((prev) => ({ ...prev, [id]: action }));
  }
  function clearDocAction(id) {
    setActionLoading((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
  }

  // ─── Retry ─────────────────────────────────────────────────────────────────

  async function handleRetry(doc) {
    setDocAction(doc.id, "retry");
    try {
      await documentsApi.retryDocument(doc.id);
      toast.success(`"${doc.filename}" queued for retry.`);
      fetchDocuments(pagination.page);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Retry failed."));
    } finally {
      clearDocAction(doc.id);
    }
  }

  // ─── Delete ────────────────────────────────────────────────────────────────

  async function handleDelete() {
    if (!deleteDoc) return;
    setDeleting(true);
    setDocAction(deleteDoc.id, "delete");
    try {
      await documentsApi.deleteDocument(deleteDoc.id);
      toast.success(`"${deleteDoc.filename}" deleted.`);
      setDeleteDoc(null);
      fetchDocuments(pagination.page);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Delete failed."));
    } finally {
      setDeleting(false);
      if (deleteDoc) clearDocAction(deleteDoc.id);
    }
  }

  // ─── Assign ────────────────────────────────────────────────────────────────

  async function handleAssign(docId, chatbotId) {
    setDocAction(docId, "assign");
    try {
      await documentsApi.assignDocument(docId, { chatbotId });
      toast.success("Document assigned successfully!");
      setAssignDoc(null);
      fetchDocuments(pagination.page);
    } finally {
      clearDocAction(docId);
    }
  }

  // ─── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      {/* Upload zone */}
      <UploadZone
        onUpload={handleUpload}
        chatbots={chatbots}
        selectedChatbotId={uploadChatbotId}
        onChatbotChange={setUploadChatbotId}
      />

      {/* Toolbar */}
      <DocumentsToolbar
        filters={{ ...filters, search: searchRaw }}
        onFilter={handleFilter}
        chatbots={chatbots}
        onClear={handleClearFilters}
      />

      {/* Table */}
      <DocumentsTable
        documents={documents}
        loading={loading}
        error={loadError}
        onRetryLoad={() => fetchDocuments(pagination.page)}
        onChunks={(doc) => setChunkDoc(doc)}
        onAssign={(doc) => setAssignDoc(doc)}
        onRetry={handleRetry}
        onDelete={(doc) => setDeleteDoc(doc)}
        actionLoading={actionLoading}
      />

      {/* Pagination */}
      <DocumentsPagination
        page={pagination.page}
        totalPages={pagination.totalPages}
        total={pagination.total}
        size={pagination.size}
        onPage={(p) => fetchDocuments(p)}
      />

      {/* Chunk Drawer */}
      <ChunkDrawer document={chunkDoc} onClose={() => setChunkDoc(null)} />

      {/* Assign Modal */}
      <AssignChatbotModal
        document={assignDoc}
        chatbots={chatbots}
        onClose={() => setAssignDoc(null)}
        onConfirm={handleAssign}
      />

      {/* Delete Confirm */}
      <ConfirmDeleteModal
        isOpen={!!deleteDoc}
        onClose={() => !deleting && setDeleteDoc(null)}
        onConfirm={handleDelete}
        loading={deleting}
        title={`Delete "${deleteDoc?.filename || "document"}"?`}
        message="Document and all its indexed chunks will be permanently removed. This cannot be undone."
        confirmText="Yes, Delete"
        cancelText="Cancel"
      />
    </div>
  );
}
