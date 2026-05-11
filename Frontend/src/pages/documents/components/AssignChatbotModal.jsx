import { useState, useEffect } from "react";
import Modal from "../../../components/common/Modal";

function getApiErrorMessage(err, fallback) {
  return (
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    err?.message ||
    fallback
  );
}

/**
 * AssignChatbotModal — select a chatbot and assign a document to it.
 *
 * Props:
 *   document    : { id, filename, chatbotId } | null
 *   chatbots    : [{ id, name }]
 *   onClose     : () => void
 *   onConfirm   : (documentId, chatbotId) => Promise<void>
 */
export default function AssignChatbotModal({ document, chatbots, onClose, onConfirm }) {
  const isOpen = !!document;
  const [selected, setSelected] = useState("");
  const [saving,   setSaving]   = useState(false);
  const [error,    setError]    = useState(null);

  // Pre-select current chatbot when modal opens
  useEffect(() => {
    if (document) {
      setSelected(document.chatbotId || "");
      setError(null);
    }
  }, [document]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!selected) {
      setError("Please select a chatbot.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onConfirm(document.id, selected);
      // onConfirm handles close + toast
    } catch (err) {
      setError(getApiErrorMessage(err, "Failed to assign document."));
    } finally {
      setSaving(false);
    }
  }

  function handleClose() {
    if (!saving) onClose();
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Assign to Chatbot"
      maxWidth="max-w-md"
    >
      {document && (
        <form onSubmit={handleSubmit} className="space-y-4">
          <p className="text-sm text-gray-600">
            Assigning:{" "}
            <span className="font-medium text-gray-800">{document.filename}</span>
          </p>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-gray-700">Chatbot</label>
            <select
              value={selected}
              onChange={(e) => { setSelected(e.target.value); setError(null); }}
              disabled={saving}
              className={`w-full px-3 py-2 text-sm border rounded-lg bg-white
                          focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                          ${error ? "border-red-400" : "border-gray-300"}
                          disabled:opacity-60`}
            >
              <option value="">— Select chatbot —</option>
              {chatbots.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
            {error && <p className="text-xs text-red-600">{error}</p>}
          </div>

          <div className="flex justify-end gap-3 pt-1">
            <button
              type="button"
              onClick={handleClose}
              disabled={saving}
              className="px-4 py-2 text-sm border border-gray-300 rounded-lg text-gray-700
                         hover:bg-gray-50 disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving || !selected}
              className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg
                         hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed
                         flex items-center gap-2"
            >
              {saving && (
                <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              )}
              {saving ? "Assigning…" : "Assign"}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
