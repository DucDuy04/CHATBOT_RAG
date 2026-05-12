import { useState } from "react";
import StatusBadge from "../../../components/common/StatusBadge";

const STATUS_OPTIONS = [
  {
    value: "ACTIVE",
    label: "Active",
    description: "Chatbot đang hoạt động và có thể phục vụ người dùng qua widget.",
  },
  {
    value: "INACTIVE",
    label: "Inactive",
    description: "Chatbot tạm dừng. Widget không nhận message mới.",
  },
];

/**
 * StatusSection — card toggle ACTIVE / INACTIVE cho chatbot.
 *
 * Props:
 *   status   — "ACTIVE" | "INACTIVE"
 *   onChange — (newStatus) => void
 *   onSave   — async () => void
 *   saving   — bool
 */
export default function StatusSection({ status, onChange, onSave, saving }) {
  const [saveError, setSaveError] = useState(null);

  async function handleSave() {
    setSaveError(null);
    try {
      await onSave();
    } catch (err) {
      setSaveError(err?.message || "Failed to save status.");
    }
  }

  return (
    <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-gray-100">
        <h3 className="text-sm font-semibold text-gray-800">Status</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Bật/tắt chatbot này.
        </p>
      </div>

      <div className="px-5 py-4 space-y-4">
        {/* Current badge */}
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-500">Current:</span>
          <StatusBadge status={status?.toLowerCase() === "active" ? "active" : "inactive"} />
        </div>

        {/* Toggle buttons */}
        <div className="flex flex-wrap gap-3">
          {STATUS_OPTIONS.map((opt) => {
            const isSelected = status === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => onChange(opt.value)}
                className={`
                  flex flex-col items-start px-4 py-3 rounded-lg border-2 text-left
                  transition-all min-w-[160px]
                  ${
                    isSelected
                      ? opt.value === "ACTIVE"
                        ? "border-green-500 bg-green-50"
                        : "border-gray-400 bg-gray-50"
                      : "border-gray-200 hover:border-gray-300 bg-white"
                  }
                `}
              >
                <span
                  className={`text-sm font-semibold ${
                    isSelected
                      ? opt.value === "ACTIVE"
                        ? "text-green-700"
                        : "text-gray-700"
                      : "text-gray-500"
                  }`}
                >
                  {isSelected && "✓ "}
                  {opt.label}
                </span>
                <span className="text-xs text-gray-400 mt-0.5 leading-snug">
                  {opt.description}
                </span>
              </button>
            );
          })}
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
            {saving ? "Saving…" : "Save Status"}
          </button>
        </div>
      </div>
    </section>
  );
}
