import { useEffect, useState } from "react";
import Drawer from "../../../components/common/Drawer";

/**
 * PromptBuilderDrawer — session-only system prompt override (not persisted to chatbot).
 *
 * Props:
 *   isOpen
 *   onClose
 *   appliedText — current applied override (sync draft when opening)
 *   onApply     — (text: string) => void  (parent trims)
 *   onClearOverride — () => void
 */
export default function PromptBuilderDrawer({
  isOpen,
  onClose,
  appliedText,
  onApply,
  onClearOverride,
}) {
  const [draft, setDraft] = useState("");

  useEffect(() => {
    if (!isOpen) return;
    Promise.resolve().then(() => {
      setDraft(appliedText || "");
    });
  }, [isOpen, appliedText]);

  const count = draft.length;

  return (
    <Drawer isOpen={isOpen} onClose={onClose} title="Prompt Builder" width="w-[28rem] max-w-full">
      <div className="space-y-4">
        <p className="text-xs text-amber-800 bg-amber-50 border border-amber-100 rounded-lg p-2">
          Override system prompt chỉ áp dụng cho phiên Playground hiện tại. Không lưu vào cấu hình
          chatbot và không gọi API cập nhật chatbot.
        </p>

        <div>
          <label className="text-xs font-medium text-gray-700 block mb-1">
            System prompt override
          </label>
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={12}
            placeholder="Nhập system prompt tùy chỉnh cho lượt test..."
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
          />
          <p className="text-xs text-gray-400 mt-1">{count} ký tự</p>
        </div>

        <div className="flex flex-wrap gap-2 pt-2 border-t">
          <button
            type="button"
            onClick={() => {
              onApply(draft);
              onClose();
            }}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700"
          >
            Apply
          </button>
          <button
            type="button"
            onClick={() => {
              setDraft("");
              onClearOverride();
            }}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            Clear override
          </button>
        </div>
      </div>
    </Drawer>
  );
}
