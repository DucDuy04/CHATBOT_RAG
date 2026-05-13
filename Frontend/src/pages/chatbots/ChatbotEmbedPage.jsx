import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import { chatbotsApi } from "../../api";
import { useLayout } from "../../contexts/LayoutContext";
import { useToast } from "../../components/common/useToast";
import ChatbotConfigTabs from "./components/ChatbotConfigTabs";
import ChatbotEmbedSkeleton from "./components/ChatbotEmbedSkeleton";
import EmbedSettingsSection from "./components/EmbedSettingsSection";
import EmbedCodeBlock from "./components/EmbedCodeBlock";
import WidgetLivePreview from "./components/WidgetLivePreview";

// Fallback frontend URL — uses env var if available, else window.location.origin
const FRONTEND_URL =
  (typeof import.meta !== "undefined" && import.meta.env?.VITE_FRONTEND_URL) ||
  window.location.origin;

/** Public widget UUID from embed-config (widgetKey or legacy apiKey). */
function resolveWidgetKey(form) {
  const k = form.widgetKey ?? form.apiKey;
  return typeof k === "string" ? k.trim() : "";
}

const ORIGIN_LINE_RE = /^https?:\/\/[a-zA-Z0-9.-]+(:\d+)?(\/.*)?$/;

/**
 * Normalize allowed origins for form + snippet: array, multiline string, or comma-separated.
 * Trims, drops blanks, dedupes (first occurrence wins).
 */
function normalizeAllowedOriginsList(raw) {
  let parts = [];
  if (raw == null) {
    parts = [];
  } else if (Array.isArray(raw)) {
    parts = raw.map((s) => String(s).trim()).filter(Boolean);
  } else if (typeof raw === "string") {
    parts = raw
      .split(/[\n\r,]+/)
      .map((s) => s.trim())
      .filter(Boolean);
  }
  const seen = new Set();
  const out = [];
  for (const p of parts) {
    if (!ORIGIN_LINE_RE.test(p)) continue;
    if (seen.has(p)) continue;
    seen.add(p);
    out.push(p);
  }
  return out;
}

/** Build embed snippet from current form values (real widgetKey when loaded). */
function buildSnippet({ form }) {
  const widgetKey = resolveWidgetKey(form);
  const allowedOrigins = normalizeAllowedOriginsList(form.allowedOrigins);
  const cfg = {
    widgetKey,
    frontendUrl: `${FRONTEND_URL}`,
    widgetColor: form.widgetColor,
    welcomeMessage: form.welcomeMessage,
    position: form.position,
    launcherIcon: form.launcherIcon,
    allowedOrigins,
  };

  const cfgJson = JSON.stringify(cfg, null, 2)
    .split("\n")
    .map((line, i) => (i === 0 ? line : "  " + line))
    .join("\n");

  return [
    `<script>`,
    `  window.RagChatbotConfig = ${cfgJson};`,
    `</script>`,
    `<script async src="${FRONTEND_URL}/dist-widget/chatbot-widget.iife.js"></script>`,
  ].join("\n");
}

/** Normalize API embed config → form state, with safe fallbacks. */
function toFormState(config) {
  return {
    widgetKey: resolveWidgetKey({
      widgetKey: config.widgetKey,
      apiKey: config.apiKey,
    }),
    widgetColor:
      config.widgetColor || config.color || "#2563eb",
    welcomeMessage: config.welcomeMessage || "",
    position:
      config.position === "bottom-left" ? "bottom-left" : "bottom-right",
    launcherIcon: config.launcherIcon || "chat",
    allowedOrigins: normalizeAllowedOriginsList(config.allowedOrigins),
  };
}

const DEFAULT_FORM = {
  widgetKey: "",
  widgetColor: "#2563eb",
  welcomeMessage: "Xin chào! Tôi có thể giúp gì cho bạn?",
  position: "bottom-right",
  launcherIcon: "chat",
  allowedOrigins: [],
};

/**
 * ChatbotEmbedPage — /chatbots/:id/embed
 *
 * Loads embed config, lets admin configure and save widget appearance.
 * Live preview + embed code snippet generated from current form state.
 * Tab bar: Config | Embed (Embed active).
 */
export default function ChatbotEmbedPage() {
  const { id } = useParams();
  const { setPageTitle, setRightSlot, clearRightSlot } = useLayout();
  const toast = useToast();
  const toastRef = useRef(toast);
  const allowedOriginsInputRef = useRef(null);

  useEffect(() => {
    toastRef.current = toast;
  }, [toast]);

  // ─── Data state ─────────────────────────────────────────────────────────────
  const [chatbot,   setChatbot]   = useState(null);
  const [loading,   setLoading]   = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [saving,    setSaving]    = useState(false);

  // ─── Form state ──────────────────────────────────────────────────────────────
  const [form, setForm] = useState(DEFAULT_FORM);

  function updateForm(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  // ─── Load data ───────────────────────────────────────────────────────────────

  const loadData = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      // Load both in parallel; if getChatbot fails we still show embed config
      const [embedConfig, bot] = await Promise.allSettled([
        chatbotsApi.getEmbedConfig(id),
        chatbotsApi.getChatbot(id),
      ]);

      if (embedConfig.status === "fulfilled") {
        setForm(toFormState(embedConfig.value));
      } else {
        // embed config not found — use defaults but report error
        setLoadError(embedConfig.reason?.message || "Failed to load embed config.");
        setForm(DEFAULT_FORM);
      }

      if (bot.status === "fulfilled") {
        setChatbot(bot.value);
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // ─── Page title ──────────────────────────────────────────────────────────────

  useEffect(() => {
    if (chatbot?.name) {
      setPageTitle(`${chatbot.name} — Embed`);
    }
  }, [chatbot?.name, setPageTitle]);

  // ─── Header rightSlot: Save button ───────────────────────────────────────────

  const handleSave = useCallback(async () => {
    if (saving) return;
    setSaving(true);
    try {
      const originsPayload = normalizeAllowedOriginsList(
        allowedOriginsInputRef.current?.getOriginsForSave?.() ?? form.allowedOrigins
      );
      const payload = {
        widgetColor:    form.widgetColor,
        welcomeMessage: form.welcomeMessage,
        position:       form.position,
        allowedOrigins: originsPayload,
        // launcherIcon included for mock/dev; may be ignored by backend if contract
        // does not include it. Report 06 documents this gap.
        launcherIcon: form.launcherIcon,
      };
      const updated = await chatbotsApi.updateEmbedConfig(id, payload);
      // Merge response into form: always take server allowedOrigins when present (never merge stale origins)
      if (updated && typeof updated === "object") {
        setForm((prev) => {
          const merged = { ...prev, ...updated };
          if (Object.prototype.hasOwnProperty.call(updated, "allowedOrigins")) {
            merged.allowedOrigins = normalizeAllowedOriginsList(updated.allowedOrigins);
          } else {
            merged.allowedOrigins = originsPayload;
          }
          return toFormState(merged);
        });
      }
      toastRef.current.success("Embed config saved!");
    } catch (err) {
      toastRef.current.error(err?.message || "Failed to save embed config.");
    } finally {
      setSaving(false);
    }
  }, [id, form, saving]);

  const saveRightSlot = useMemo(
    () => (
      <button
        onClick={handleSave}
        disabled={saving || loading}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium
                   bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors
                   disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {saving ? (
          <>
            <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
            Saving…
          </>
        ) : (
          "💾 Save"
        )}
      </button>
    ),
    [handleSave, saving, loading]
  );

  useEffect(() => {
    setRightSlot(saveRightSlot);
  }, [saveRightSlot, setRightSlot]);

  useEffect(() => {
    return () => clearRightSlot();
  }, [clearRightSlot]);

  // ─── Derived values ──────────────────────────────────────────────────────────

  const snippet = buildSnippet({ form });
  const hasWidgetKey = Boolean(resolveWidgetKey(form));

  // ─── Render: loading ─────────────────────────────────────────────────────────

  if (loading) {
    return <ChatbotEmbedSkeleton />;
  }

  // ─── Render: hard error (embed config not found + no fallback possible) ──────

  if (loadError && !form.widgetColor) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center space-y-4">
        <p className="text-lg">⚠️</p>
        <p className="text-sm font-semibold text-red-700">Failed to load embed config</p>
        <p className="text-sm text-red-500">{loadError}</p>
        <div className="flex justify-center gap-3">
          <button
            onClick={loadData}
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

  // ─── Render: main ────────────────────────────────────────────────────────────

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

      {/* Partial load warning */}
      {loadError && (
        <div className="rounded-lg border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-700">
          ⚠️ {loadError} — showing default values.
        </div>
      )}

      {/* Tab bar */}
      <ChatbotConfigTabs id={id} />

      {/* Main content: two-column on lg */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left: settings */}
        <EmbedSettingsSection
          form={form}
          onChange={updateForm}
          onSave={handleSave}
          saving={saving}
          allowedOriginsInputRef={allowedOriginsInputRef}
        />

        {/* Right: preview + code block */}
        <div className="space-y-5">
          <WidgetLivePreview
            widgetColor={form.widgetColor}
            welcomeMessage={form.welcomeMessage}
            position={form.position}
            launcherIcon={form.launcherIcon}
            allowedOrigins={form.allowedOrigins}
          />
          {!hasWidgetKey && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              Thiếu public widget key. Vui lòng refresh trang hoặc tạo lại chatbot. Snippet bên dưới có
              <code className="mx-1 rounded bg-amber-100 px-1">widgetKey</code> rỗng — không dùng được cho
              widget cho đến khi key được tải từ API.
            </div>
          )}
          <EmbedCodeBlock snippet={snippet} copyDisabled={!hasWidgetKey} />
        </div>
      </div>
    </div>
  );
}
