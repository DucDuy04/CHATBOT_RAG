import { useState } from "react";

/**
 * Model options — bao gồm cả GPT models (spec) lẫn Groq/Llama.
 * Nếu modelConfig.model từ API không nằm trong list này, vẫn hiển thị được.
 */
const MODEL_OPTIONS = [
  { value: "GPT-4o",                    label: "GPT-4o" },
  { value: "GPT-3.5",                   label: "GPT-3.5 Turbo" },
  { value: "llama-3.1-70b-versatile",   label: "Llama 3.1 70B (Groq)" },
  { value: "llama3-8b-8192",            label: "Llama 3 8B (Groq)" },
];

/**
 * ModelSettingsSection — card cấu hình model: model, temperature, topK, maxTokens.
 *
 * Props:
 *   model, temperature, topK, maxTokens — current values
 *   onChange(key, value) — update single field
 *   onSave — async () => void
 *   saving — bool
 */
export default function ModelSettingsSection({
  model,
  temperature,
  topK,
  maxTokens,
  onChange,
  onSave,
  saving,
}) {
  const [saveError, setSaveError] = useState(null);

  // Validation
  const tempInvalid = temperature < 0 || temperature > 1;
  const topKInvalid = !Number.isInteger(Number(topK)) || Number(topK) < 1 || Number(topK) > 30;
  const maxTokInvalid = !Number.isInteger(Number(maxTokens)) || Number(maxTokens) < 100 || Number(maxTokens) > 8000;
  const isInvalid = tempInvalid || topKInvalid || maxTokInvalid;

  async function handleSave() {
    if (isInvalid) return;
    setSaveError(null);
    try {
      await onSave();
    } catch (err) {
      setSaveError(err?.message || "Failed to save model settings.");
    }
  }

  return (
    <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-gray-100">
        <h3 className="text-sm font-semibold text-gray-800">Model Settings</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Cấu hình model AI và các tham số sinh văn bản.
        </p>
      </div>

      <div className="px-5 py-4 space-y-5">
        {/* Model select */}
        <div>
          <label className="block text-xs font-semibold text-gray-600 mb-1.5">
            Model
          </label>
          <select
            value={model}
            onChange={(e) => onChange("model", e.target.value)}
            className="w-full sm:w-auto min-w-[220px] px-3 py-2 text-sm border border-gray-300 rounded-lg bg-white
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            {MODEL_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
            {/* Nếu model hiện tại không có trong list, thêm vào */}
            {model && !MODEL_OPTIONS.find((o) => o.value === model) && (
              <option value={model}>{model}</option>
            )}
          </select>
        </div>

        {/* Temperature slider */}
        <div>
          <label className="block text-xs font-semibold text-gray-600 mb-1.5">
            Temperature{" "}
            <span className="font-normal text-gray-400 ml-1">
              (0 = deterministic, 1 = creative)
            </span>
          </label>
          <div className="flex items-center gap-3">
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={temperature}
              onChange={(e) => onChange("temperature", parseFloat(e.target.value))}
              className="flex-1 h-2 rounded-lg appearance-none cursor-pointer accent-blue-600"
            />
            <span
              className={`text-sm font-mono font-semibold min-w-[3rem] text-right
                          ${tempInvalid ? "text-red-600" : "text-gray-700"}`}
            >
              {Number(temperature).toFixed(2)}
            </span>
          </div>
          {tempInvalid && (
            <p className="text-xs text-red-500 mt-1">Must be between 0 and 1.</p>
          )}
        </div>

        {/* Top-K + Max tokens: 2 column on sm+ */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {/* Top-K */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1.5">
              Context Top-N{" "}
              <span className="font-normal text-gray-400">(1–30)</span>
            </label>
            <input
              type="number"
              min={1}
              max={30}
              step={1}
              value={topK}
              onChange={(e) => onChange("topK", parseInt(e.target.value, 10))}
              className={`w-full px-3 py-2 text-sm border rounded-lg
                focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                ${topKInvalid ? "border-red-400 bg-red-50" : "border-gray-300"}`}
            />
            {topKInvalid && (
              <p className="text-xs text-red-500 mt-1">Must be between 1 and 50.</p>
            )}
          </div>

          {/* Max tokens */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1.5">
              Max Tokens{" "}
              <span className="font-normal text-gray-400">(100–8000)</span>
            </label>
            <input
              type="number"
              min={100}
              max={8000}
              step={100}
              value={maxTokens}
              onChange={(e) => onChange("maxTokens", parseInt(e.target.value, 10))}
              className={`w-full px-3 py-2 text-sm border rounded-lg
                focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                ${maxTokInvalid ? "border-red-400 bg-red-50" : "border-gray-300"}`}
            />
            {maxTokInvalid && (
              <p className="text-xs text-red-500 mt-1">Must be between 100 and 8000.</p>
            )}
          </div>
        </div>

        {/* Save error */}
        {saveError && (
          <p className="text-sm text-red-600 bg-red-50 border border-red-100 px-3 py-2 rounded-lg">
            {saveError}
          </p>
        )}

        {/* Save button */}
        <div className="flex justify-end">
          <button
            type="button"
            onClick={handleSave}
            disabled={saving || isInvalid}
            className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg
                       hover:bg-blue-700 active:bg-blue-800 transition-colors
                       disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {saving && (
              <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
            )}
            {saving ? "Saving…" : "Save Model Settings"}
          </button>
        </div>
      </div>
    </section>
  );
}
