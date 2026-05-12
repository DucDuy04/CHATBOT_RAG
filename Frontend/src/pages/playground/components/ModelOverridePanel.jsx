import { useCallback } from "react";

/**
 * ModelOverridePanel — right panel for per-session model parameter overrides.
 * Changes here only affect the current playground session; they are NOT saved
 * to the chatbot's persistent config.
 *
 * Props:
 *   params   — { temperature: number, topK: number, maxTokens: number }
 *   onChange — (newParams) => void
 */
export default function ModelOverridePanel({ params, onChange }) {
  const handleChange = useCallback(
    (key, value) => {
      onChange({ ...params, [key]: value });
    },
    [params, onChange]
  );

  return (
    <div className="p-3 space-y-4">
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
        Model Override
      </p>

      {/* Temperature slider */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-600 font-medium">Temperature</label>
          <span className="text-xs font-mono text-gray-700 bg-gray-100 px-1.5 py-0.5 rounded">
            {params.temperature.toFixed(2)}
          </span>
        </div>
        <input
          type="range"
          min="0"
          max="1"
          step="0.01"
          value={params.temperature}
          onChange={(e) => handleChange("temperature", parseFloat(e.target.value))}
          className="w-full h-1.5 accent-blue-600 cursor-pointer"
        />
        <div className="flex justify-between text-xs text-gray-400 mt-0.5">
          <span>0 (precise)</span>
          <span>1 (creative)</span>
        </div>
      </div>

      {/* Top-K */}
      <div>
        <label className="text-xs text-gray-600 font-medium block mb-1">
          Top-K (retrieval)
        </label>
        <input
          type="number"
          min="1"
          max="20"
          value={params.topK}
          onChange={(e) =>
            handleChange("topK", Math.max(1, parseInt(e.target.value, 10) || 1))
          }
          className="w-full px-2 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
      </div>

      {/* Max Tokens */}
      <div>
        <label className="text-xs text-gray-600 font-medium block mb-1">
          Max Tokens
        </label>
        <input
          type="number"
          min="64"
          max="4096"
          step="64"
          value={params.maxTokens}
          onChange={(e) =>
            handleChange(
              "maxTokens",
              Math.max(64, parseInt(e.target.value, 10) || 64)
            )
          }
          className="w-full px-2 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
      </div>

      <p className="text-xs text-gray-400 italic">
        Overrides chỉ áp dụng cho session playground hiện tại, không lưu vào cấu hình chatbot.
      </p>
      <p className="text-xs text-gray-400 italic border-t border-gray-100 pt-2 mt-1">
        <strong className="font-medium text-gray-500">Temperature</strong>: độ ngẫu nhiên của câu trả lời — thấp hơn
        thường ổn định/chính xác hơn, cao hơn đa dạng hơn.
        <br />
        <strong className="font-medium text-gray-500">Top-K</strong>: số chunk tối đa lấy từ retrieval để đưa vào
        context (panel Sources bên phải chỉ hiển thị tối đa K mục để tránh nhầm với số chunk thực tế backend có thể
        dùng).
        <br />
        <strong className="font-medium text-gray-500">Max tokens</strong>: giới hạn độ dài (ước lượng) của câu trả lời
        từ model.
        <br />
        <span className="not-italic text-[10px] text-amber-800/90">
          MVP: một số override có thể chưa được backend áp dụng sâu cho mọi đường RAG; nếu câu trả lời không đổi khi
          chỉnh nhẹ, đó là giới hạn hiện tại chứ không phải lỗi UI.
        </span>
      </p>
      <p className="text-xs text-gray-400">
        System prompt: dùng nút <strong className="font-medium">Prompt Builder</strong> trên thanh công cụ.
      </p>
    </div>
  );
}
