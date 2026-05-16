import { useCallback } from "react";

/**
 * CompareConfigPanel — temperature / topK / maxTokens / optional systemPrompt for one compare side.
 * Per-side systemPrompt: if empty at request time, parent falls back to Prompt Builder override.
 *
 * Props:
 *   label   — "Config A" | "Config B"
 *   config  — { temperature, topK, maxTokens, systemPrompt }
 *   onChange — (next) => void
 */
export default function CompareConfigPanel({ label, config, onChange }) {
  const handleChange = useCallback(
    (key, value) => {
      onChange({ ...config, [key]: value });
    },
    [config, onChange]
  );

  return (
    <div className="rounded-lg border border-gray-200 bg-gray-50/80 p-3 space-y-3">
      <p className="text-xs font-semibold text-gray-600">{label}</p>

      <p className="text-[11px] text-gray-500 leading-snug">
        Temperature: độ ngẫu nhiên — thấp ổn định hơn, cao đa dạng hơn. Top-K: số chunk retrieval tối đa đưa vào
        context (cột Sources trong compare chỉ liệt kê tối đa K dòng để khớp cấu hình). Max tokens: giới hạn độ dài câu
        trả lời.
      </p>

      <div>
        <div className="flex items-center justify-between mb-1">
          <span className="text-xs text-gray-600">Temperature</span>
          <span className="text-xs font-mono bg-white px-1 rounded border">
            {Number(config.temperature).toFixed(2)}
          </span>
        </div>
        <input
          type="range"
          min="0"
          max="1"
          step="0.01"
          value={config.temperature}
          onChange={(e) =>
            handleChange("temperature", Math.min(1, Math.max(0, parseFloat(e.target.value) || 0)))
          }
          className="w-full h-1.5 accent-blue-600"
        />
      </div>

      <div>
        <label className="text-xs text-gray-600 block mb-1">Top-K</label>
        <input
          type="number"
          min="1"
          max="30"
          value={config.topK}
          onChange={(e) => {
            const parsed = parseInt(e.target.value, 10);
            const n = Number.isFinite(parsed) ? parsed : 1;
            handleChange("topK", Math.min(30, Math.max(1, n)));
          }}
          className="w-full px-2 py-1 text-xs border rounded-lg"
        />
      </div>

      <div>
        <label className="text-xs text-gray-600 block mb-1">Max tokens</label>
        <input
          type="number"
          min="64"
          max="4096"
          step="64"
          value={config.maxTokens}
          onChange={(e) =>
            handleChange("maxTokens", Math.max(64, parseInt(e.target.value, 10) || 64))
          }
          className="w-full px-2 py-1 text-xs border rounded-lg"
        />
      </div>

      <div>
        <label className="text-xs text-gray-600 block mb-1">
          System prompt (optional)
        </label>
        <textarea
          value={config.systemPrompt || ""}
          onChange={(e) => handleChange("systemPrompt", e.target.value)}
          placeholder="Leave empty to use Prompt Builder override (if any)"
          rows={2}
          className="w-full px-2 py-1 text-xs border rounded-lg resize-none"
        />
      </div>
    </div>
  );
}
