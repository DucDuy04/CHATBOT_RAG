import { useEffect, useRef } from "react";
import MessageBubble from "./MessageBubble";
import EmptyState from "../../../components/common/EmptyState";

/**
 * ChatWindow — center panel with message history + input box.
 *
 * Props:
 *   messages       — array of message objects
 *   isStreaming    — bool, true while assistant is responding
 *   input          — current textarea value
 *   onInputChange  — (value: string) => void
 *   onSend         — () => void
 *   disabled       — bool, true when no chatbot is selected
 *   selectedSource — highlighted source object or null
 *   onSourceClick  — (source | null) => void
 */
export default function ChatWindow({
  messages,
  isStreaming,
  input,
  onInputChange,
  onSend,
  disabled,
  selectedSource,
  onSourceClick,
}) {
  const bottomRef = useRef(null);

  /* Auto-scroll to bottom when messages update */
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      onSend();
    }
  };

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Message list */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50 min-h-0">
        {isStreaming && (
          <div className="sticky top-0 z-10 flex items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-xs text-blue-900 shadow-sm">
            <span className="inline-block h-2 w-2 shrink-0 rounded-full bg-blue-500 animate-pulse" aria-hidden />
            <span>
              Đang stream câu trả lời — nội dung có thể cập nhật rất nhanh; khi xong, nguồn tham chiếu sẽ hiện bên phải (nếu có).
            </span>
          </div>
        )}
        {messages.length === 0 ? (
          <EmptyState
            icon="💬"
            title="Chưa có tin nhắn"
            message={
              disabled
                ? "Chọn chatbot để bắt đầu kiểm thử."
                : "Gõ câu hỏi bên dưới để bắt đầu."
            }
          />
        ) : (
          messages.map((msg) => (
            <MessageBubble
              key={msg.id}
              message={msg}
              selectedSource={selectedSource}
              onSourceClick={onSourceClick}
            />
          ))
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input area */}
      <div className="shrink-0 flex gap-2 p-3 bg-white border-t">
        <textarea
          value={input}
          onChange={(e) => onInputChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={
            disabled
              ? "Chọn chatbot trước..."
              : "Nhập câu hỏi... (Enter gửi · Shift+Enter xuống dòng)"
          }
          disabled={disabled || isStreaming}
          rows={2}
          className="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-xl resize-none outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
        />
        <button
          onClick={onSend}
          disabled={disabled || isStreaming || !input.trim()}
          className="self-end px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed shrink-0 transition-colors"
        >
          {isStreaming ? (
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
              Đang trả lời
            </span>
          ) : (
            "Gửi"
          )}
        </button>
      </div>
    </div>
  );
}
