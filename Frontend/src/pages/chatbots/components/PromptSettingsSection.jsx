import { useRef, useState } from "react";

const CHAR_WARN_LIMIT = 4000;

const VARIABLE_HINTS = [
  { label: "{user_name}", value: "{user_name}" },
  { label: "{date}", value: "{date}" },
];

/**
 * PromptSettingsSection — card chỉnh sửa system prompt.
 *
 * Props:
 *   value       — current systemPrompt string
 *   onChange    — (newValue) => void
 *   onSave      — async () => void — được gọi khi click Save
 *   saving      — bool khi đang lưu
 */
export default function PromptSettingsSection({ value, onChange, onSave, saving }) {
  const textareaRef = useRef(null);
  const [saveError, setSaveError] = useState(null);

  const charCount = value.length;
  const isOverLimit = charCount > CHAR_WARN_LIMIT;

  /** Insert variable hint tại cursor position (fallback: cuối chuỗi). */
  function insertVariable(variable) {
    const el = textareaRef.current;
    if (!el) {
      onChange(value + variable);
      return;
    }
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? value.length;
    const newValue = value.slice(0, start) + variable + value.slice(end);
    onChange(newValue);
    // Restore cursor sau insert
    requestAnimationFrame(() => {
      el.focus();
      el.selectionStart = start + variable.length;
      el.selectionEnd = start + variable.length;
    });
  }

  async function handleSave() {
    setSaveError(null);
    try {
      await onSave();
    } catch (err) {
      setSaveError(err?.message || "Failed to save prompt.");
    }
  }

  return (
    <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-gray-100">
        <h3 className="text-sm font-semibold text-gray-800">System Prompt</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Hướng dẫn hành vi của chatbot. Dùng biến để cá nhân hóa.
        </p>
      </div>

      <div className="px-5 py-4 space-y-4">
        {/* Variable hints */}
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-xs text-gray-400">Insert variable:</span>
          {VARIABLE_HINTS.map((hint) => (
            <button
              key={hint.value}
              type="button"
              onClick={() => insertVariable(hint.value)}
              className="px-2 py-1 text-xs font-mono bg-blue-50 text-blue-700 border border-blue-200
                         rounded hover:bg-blue-100 transition-colors"
              title={`Insert ${hint.value}`}
            >
              {hint.label}
            </button>
          ))}
        </div>

        {/* Textarea */}
        <div>
          <textarea
            ref={textareaRef}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            rows={8}
            placeholder="Bạn là trợ lý AI chuyên nghiệp. Trả lời dựa trên tài liệu được cung cấp..."
            className={`w-full px-3 py-2 text-sm border rounded-lg resize-y
              focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
              font-mono leading-relaxed
              ${isOverLimit ? "border-amber-400 bg-amber-50" : "border-gray-300"}`}
          />
          {/* Char count */}
          <div className="flex justify-end mt-1">
            <span
              className={`text-xs ${
                isOverLimit ? "text-amber-600 font-medium" : "text-gray-400"
              }`}
            >
              {charCount.toLocaleString()} chars
              {isOverLimit && ` — exceeds recommended ${CHAR_WARN_LIMIT.toLocaleString()}`}
            </span>
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
            disabled={saving}
            className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg
                       hover:bg-blue-700 active:bg-blue-800 transition-colors
                       disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {saving && (
              <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
            )}
            {saving ? "Saving…" : "Save Prompt"}
          </button>
        </div>
      </div>
    </section>
  );
}
