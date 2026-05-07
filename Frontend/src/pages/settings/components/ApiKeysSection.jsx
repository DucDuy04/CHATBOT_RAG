import { useEffect, useState } from "react";
import { settingsApi } from "../../../api/settingsApi";
import { ConfirmDeleteModal, EmptyState, SkeletonLoader, useToast } from "../../../components/common";
import ApiKeyRow from "./ApiKeyRow";

export default function ApiKeysSection() {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [keys, setKeys] = useState([]);
  const [newKeyName, setNewKeyName] = useState("");
  const [generating, setGenerating] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [latestPlainTextKey, setLatestPlainTextKey] = useState(null);

  const loadKeys = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await settingsApi.getApiKeys();
      setKeys(Array.isArray(data) ? data : []);
    } catch {
      setError("Failed to load API keys.");
      toast.error("Failed to load API keys.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadKeys();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const payload = { name: newKeyName.trim() || "Generated Key" };
      const res = await settingsApi.generateApiKey(payload);
      setLatestPlainTextKey(res?.plainTextKey || null);
      toast.success("New API key generated.");
      setNewKeyName("");
      await loadKeys();
    } catch {
      toast.error("Failed to generate API key.");
    } finally {
      setGenerating(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget?.id) return;
    setDeleting(true);
    try {
      await settingsApi.deleteApiKey(deleteTarget.id);
      toast.success("API key deleted.");
      setDeleteTarget(null);
      await loadKeys();
    } catch {
      toast.error("Failed to delete API key.");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-[220px] flex-1">
          <p className="text-sm font-semibold text-gray-700">API Keys</p>
          <p className="text-xs text-gray-500">Generate and manage API keys for integrations.</p>
        </div>
        <div className="min-w-[220px]">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Key name</label>
          <input
            type="text"
            value={newKeyName}
            onChange={(e) => setNewKeyName(e.target.value)}
            placeholder="e.g. Production Widget Key"
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
          />
        </div>
        <button
          type="button"
          onClick={handleGenerate}
          disabled={generating}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {generating ? "Generating..." : "Generate new key"}
        </button>
      </div>

      {latestPlainTextKey && (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 p-3">
          <p className="text-sm font-semibold text-amber-800">Copy this key now. It will not be shown again.</p>
          <p className="mt-2 select-all break-all rounded bg-white px-2 py-1 font-mono text-xs text-gray-700">
            {latestPlainTextKey}
          </p>
        </div>
      )}

      {loading && <SkeletonLoader variant="table" count={4} />}

      {!loading && error && (
        <div className="rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-600">
          <p>{error}</p>
          <button
            type="button"
            onClick={loadKeys}
            className="mt-2 rounded border border-red-200 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-100"
          >
            Retry
          </button>
        </div>
      )}

      {!loading && !error && keys.length === 0 && (
        <EmptyState
          icon="🔑"
          title="No API keys"
          message="Generate a new key to start connecting external clients."
        />
      )}

      {!loading && !error && keys.length > 0 && (
        <div className="space-y-3">
          {keys.map((item) => (
            <ApiKeyRow key={item.id} item={item} onDelete={setDeleteTarget} />
          ))}
        </div>
      )}

      <ConfirmDeleteModal
        isOpen={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
        loading={deleting}
        title="Delete API key"
        message={`Are you sure you want to delete "${deleteTarget?.name || "this key"}"? This action cannot be undone.`}
        confirmText="Delete key"
        cancelText="Cancel"
      />
    </section>
  );
}
