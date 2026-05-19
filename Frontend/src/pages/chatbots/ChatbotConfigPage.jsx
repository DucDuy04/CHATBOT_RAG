import { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { chatbotsApi } from "../../api";
import { useLayout } from "../../contexts/LayoutContext";
import { useToast } from "../../components/common/useToast";
import { ConfirmDeleteModal } from "../../components/common";
import ChatbotConfigTabs from "./components/ChatbotConfigTabs";
import ChatbotConfigSkeleton from "./components/ChatbotConfigSkeleton";
import PromptSettingsSection from "./components/PromptSettingsSection";
import ModelSettingsSection from "./components/ModelSettingsSection";
import StatusSection from "./components/StatusSection";

/** Normalize chatbot từ API thành form state, fallback an toàn nếu thiếu field. */
function toFormState(bot) {
  return {
    systemPrompt: bot.systemPrompt || "",
    model:        bot.modelConfig?.model        || "GPT-4o",
    temperature:  bot.modelConfig?.temperature  ?? 0.7,
    topK:         bot.modelConfig?.topK         ?? 5,
    maxTokens:    bot.modelConfig?.maxTokens    || 1024,
    status:       bot.status                    || "ACTIVE",
  };
}

/**
 * ChatbotConfigPage — /chatbots/:id/config
 *
 * Load chatbot → render 3 sections: Prompt, Model, Status.
 * Save per section → chatbotsApi.updateChatbot(id, payload).
 * Delete → ConfirmDeleteModal → chatbotsApi.deleteChatbot(id) → redirect /chatbots.
 * Header rightSlot: Delete button.
 * Header title: "<bot.name> — Config" sau khi load.
 */
export default function ChatbotConfigPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { setPageTitle, setRightSlot, clearRightSlot } = useLayout();
  const toast = useToast();

  // ─── Load state ───────────────────────────────────────────────────────────
  const [chatbot,  setChatbot]  = useState(null);
  const [loading,  setLoading]  = useState(true);
  const [loadErr,  setLoadErr]  = useState(null);

  // ─── Form state ───────────────────────────────────────────────────────────
  const [form, setForm] = useState({
    systemPrompt: "",
    model:        "GPT-4o",
    temperature:  0.7,
    topK:         5,
    maxTokens:    1024,
    status:       "ACTIVE",
  });

  // ─── Save states ──────────────────────────────────────────────────────────
  const [savingPrompt,  setSavingPrompt]  = useState(false);
  const [savingModel,   setSavingModel]   = useState(false);
  const [savingStatus,  setSavingStatus]  = useState(false);

  // ─── Delete states ────────────────────────────────────────────────────────
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting,   setDeleting]   = useState(false);

  // ─── Load chatbot ─────────────────────────────────────────────────────────

  const loadChatbot = useCallback(async () => {
    setLoading(true);
    setLoadErr(null);
    try {
      const bot = await chatbotsApi.getChatbot(id);
      setChatbot(bot);
      setForm(toFormState(bot));
    } catch (err) {
      setLoadErr(err?.message || "Failed to load chatbot.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadChatbot();
  }, [loadChatbot]);

  // ─── Set page title khi bot load xong ────────────────────────────────────

  useEffect(() => {
    if (chatbot?.name) {
      setPageTitle(`${chatbot.name} — Config`);
    }
  }, [chatbot?.name, setPageTitle]);

  // ─── Inject Delete button vào Header rightSlot ────────────────────────────

  useEffect(() => {
    setRightSlot(
      <button
        onClick={() => setDeleteOpen(true)}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium
                   text-red-600 bg-red-50 border border-red-200 rounded-lg
                   hover:bg-red-100 transition-colors"
      >
        🗑 Delete
      </button>
    );
  }, [setRightSlot]);

  useEffect(() => {
    return () => clearRightSlot();
  }, [clearRightSlot]);

  // ─── Form helpers ─────────────────────────────────────────────────────────

  function updateForm(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  /** Build full payload để tránh overwrite section khác trong API. */
  function buildPayload(overrides = {}) {
    return {
      systemPrompt: form.systemPrompt,
      modelConfig: {
        model:       form.model,
        temperature: form.temperature,
        topK:        form.topK,
        maxTokens:   form.maxTokens,
      },
      status: form.status,
      ...overrides,
    };
  }

  /** Sau save, merge response vào local chatbot state nếu API trả về object. */
  function applyUpdateResponse(updated) {
    if (updated && updated.id) {
      setChatbot(updated);
      // Không reset form — chỉ cập nhật field từ response để giữ unsaved changes khác
    }
  }

  // ─── Save handlers ────────────────────────────────────────────────────────

  async function handleSavePrompt() {
    setSavingPrompt(true);
    try {
      const updated = await chatbotsApi.updateChatbot(id, buildPayload({
        systemPrompt: form.systemPrompt,
      }));
      applyUpdateResponse(updated);
      toast.success("System prompt saved!");
    } finally {
      setSavingPrompt(false);
    }
  }

  async function handleSaveModel() {
    setSavingModel(true);
    try {
      const updated = await chatbotsApi.updateChatbot(id, buildPayload({
        modelConfig: {
          model:       form.model,
          temperature: form.temperature,
          topK:        form.topK,
          maxTokens:   form.maxTokens,
        },
      }));
      applyUpdateResponse(updated);
      if (updated?.modelConfig) {
        setForm((f) => ({
          ...f,
          model: updated.modelConfig.model ?? f.model,
          temperature: updated.modelConfig.temperature ?? f.temperature,
          topK: updated.modelConfig.topK ?? f.topK,
          maxTokens: updated.modelConfig.maxTokens ?? f.maxTokens,
        }));
      }
      toast.success("Model settings saved!");
    } finally {
      setSavingModel(false);
    }
  }

  async function handleSaveStatus() {
    setSavingStatus(true);
    try {
      const updated = await chatbotsApi.updateChatbot(id, buildPayload({
        status: form.status,
      }));
      applyUpdateResponse(updated);
      toast.success(`Status updated to ${form.status === "ACTIVE" ? "Active" : "Inactive"}!`);
    } finally {
      setSavingStatus(false);
    }
  }

  // ─── Delete handler ───────────────────────────────────────────────────────

  async function handleDelete() {
    setDeleting(true);
    try {
      await chatbotsApi.deleteChatbot(id);
      toast.success(`"${chatbot?.name || "Chatbot"}" has been deleted.`);
      setDeleteOpen(false);
      navigate("/chatbots");
    } catch (err) {
      toast.error(err?.message || "Failed to delete chatbot.");
      setDeleting(false);
    }
  }

  // ─── Render: loading ──────────────────────────────────────────────────────

  if (loading) {
    return <ChatbotConfigSkeleton />;
  }

  // ─── Render: error ────────────────────────────────────────────────────────

  if (loadErr) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center space-y-4">
        <p className="text-lg">⚠️</p>
        <p className="text-sm font-semibold text-red-700">
          Failed to load chatbot
        </p>
        <p className="text-sm text-red-500">{loadErr}</p>
        <div className="flex justify-center gap-3">
          <button
            onClick={loadChatbot}
            className="px-4 py-2 text-sm font-medium bg-red-600 text-white rounded-lg hover:bg-red-700"
          >
            Retry
          </button>
          <Link
            to="/chatbots"
            className="px-4 py-2 text-sm font-medium border border-gray-300 text-gray-600 rounded-lg hover:bg-gray-50"
          >
            ← Back to Chatbots
          </Link>
        </div>
      </div>
    );
  }

  // ─── Render: main ─────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      {/* Back link */}
      <Link
        to="/chatbots"
        className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-blue-600 transition-colors"
      >
        ← Chatbots
      </Link>

      {/* Bot name + domain */}
      {chatbot && (
        <div>
          <h2 className="text-lg font-bold text-gray-900">{chatbot.name}</h2>
          {chatbot.domain && (
            <p className="text-xs text-gray-400 mt-0.5">{chatbot.domain}</p>
          )}
        </div>
      )}

      {/* Tab bar */}
      <ChatbotConfigTabs id={id} />

      {/* Config sections */}
      <PromptSettingsSection
        value={form.systemPrompt}
        onChange={(v) => updateForm("systemPrompt", v)}
        onSave={handleSavePrompt}
        saving={savingPrompt}
      />

      <ModelSettingsSection
        model={form.model}
        temperature={form.temperature}
        topK={form.topK}
        maxTokens={form.maxTokens}
        onChange={updateForm}
        onSave={handleSaveModel}
        saving={savingModel}
      />

      <StatusSection
        status={form.status}
        onChange={(v) => updateForm("status", v)}
        onSave={handleSaveStatus}
        saving={savingStatus}
      />

      {/* Danger zone */}
      <section className="rounded-xl border border-red-200 bg-red-50 overflow-hidden">
        <div className="px-5 py-4 border-b border-red-100">
          <h3 className="text-sm font-semibold text-red-700">Danger Zone</h3>
        </div>
        <div className="px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
          <div>
            <p className="text-sm font-medium text-gray-700">Delete this chatbot</p>
            <p className="text-xs text-gray-500 mt-0.5">
              Soft delete. Widget/public chat sẽ ngừng hoạt động. Không thể hoàn tác từ UI.
            </p>
          </div>
          <button
            onClick={() => setDeleteOpen(true)}
            className="px-4 py-2 text-sm font-medium text-red-700 border border-red-300
                       rounded-lg hover:bg-red-100 transition-colors flex-shrink-0"
          >
            Delete Chatbot
          </button>
        </div>
      </section>

      {/* Delete confirm modal */}
      <ConfirmDeleteModal
        isOpen={deleteOpen}
        onClose={() => !deleting && setDeleteOpen(false)}
        onConfirm={handleDelete}
        loading={deleting}
        title={`Delete "${chatbot?.name || "Chatbot"}"?`}
        message={`Chatbot này sẽ bị soft delete. Widget và public chat sẽ ngừng hoạt động ngay. Bạn có chắc muốn tiếp tục?`}
        confirmText="Yes, Delete"
        cancelText="Cancel"
      />
    </div>
  );
}
