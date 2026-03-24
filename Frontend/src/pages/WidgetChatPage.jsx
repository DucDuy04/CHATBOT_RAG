import { useState, useRef, useEffect } from "react";
import { v4 as uuidv4 } from "uuid";

const API_URL = import.meta.env.VITE_API_URL;

const getSessionId = () => {
  const stored = localStorage.getItem("widget_session_id");
  if (stored) return stored;
  const newId = uuidv4();
  localStorage.setItem("widget_session_id", newId);
  return newId;
};

export default function WidgetChatPage() {
  const [messages, setMessages] = useState([
    {
      id: "welcome",
      role: "assistant",
      content: "Xin chào! Tôi có thể giúp gì cho bạn?",
    },
  ]);
  const [input, setInput]     = useState("");
  const [loading, setLoading] = useState(false);
  const bottomRef             = useRef(null);
  const sessionId             = getSessionId();

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMessage = {
      id: uuidv4(),
      role: "user",
      content: input.trim(),
    };

    const botMessageId = uuidv4();
    const botPlaceholder = {
      id: botMessageId,
      role: "assistant",
      content: "",
      streaming: true,
      sources: [],
    };

    setMessages((prev) => [...prev, userMessage, botPlaceholder]);
    setInput("");
    setLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/chat/stream`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId, message: userMessage.content }),
      });

      if (!response.ok) throw new Error("Lỗi kết nối");

      const reader  = response.body.getReader();
      const decoder = new TextDecoder();
      let   buffer  = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split("\n\n");
        buffer = events.pop();

        for (const eventStr of events) {
          if (!eventStr.trim()) continue;
          const lines     = eventStr.split("\n");
          const eventLine = lines.find((l) => l.startsWith("event:"));
          const dataLine  = lines.find((l) => l.startsWith("data:"));
          if (!eventLine || !dataLine) continue;

          const eventName = eventLine.replace("event:", "").trim();

          // FIX: KHÔNG dùng .trim() cho eventData - giữ nguyên khoảng trắng
          const eventData = dataLine.startsWith("data: ")
            ? dataLine.slice(6)   // bỏ "data: " (6 ký tự)
            : dataLine.slice(5);  // bỏ "data:"  (5 ký tự)

          if (eventName === "token") {
            setMessages((prev) =>
              prev.map((msg) => {
                if (msg.id !== botMessageId) return msg;

                return {
                  ...msg,
                  content: msg.content + eventData
                };
              })
            );
          } else if (eventName === "done") {
            let sources = [];
            try { sources = JSON.parse(eventData); } catch { sources = []; }
            setMessages((prev) =>
              prev.map((msg) =>
                msg.id === botMessageId
                  ? { ...msg, streaming: false, sources }
                  : msg
              )
            );
          }
        }
      }
    } catch (error) {
      console.error("Lỗi:", error);
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === botMessageId
            ? { ...msg, content: "Có lỗi xảy ra, vui lòng thử lại.", streaming: false }
            : msg
        )
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    // Toàn bộ trang chiếm đúng 100% iframe, không có navbar
    <div className="flex flex-col h-screen bg-white">

      {/* Header nhỏ gọn */}
      <div className="px-4 py-3 border-b bg-blue-600 text-white font-medium text-sm">
        Trợ lý AI
      </div>

      {/* Tin nhắn */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3 bg-gray-50">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`max-w-[85%] rounded-2xl px-3 py-2 text-sm overflow-hidden
                ${msg.role === "user"
                  ? "bg-blue-600 text-white rounded-br-sm"
                  : "bg-white border text-gray-800 rounded-bl-sm"}`}
            >
              <p className="whitespace-pre-wrap break-words" style={{ overflowWrap: "anywhere" }}>
                {msg.content}
                {msg.streaming && (
                  <span className="inline-block w-0.5 h-3 bg-gray-400
                                   ml-0.5 animate-pulse align-middle" />
                )}
              </p>

              {!msg.streaming && msg.sources && msg.sources.length > 0 && (
                <details className="mt-1 text-xs text-gray-400">
                  <summary className="cursor-pointer">
                    Nguồn ({msg.sources.length})
                  </summary>
                  <div className="mt-1 space-y-1">
                    {msg.sources.map((src, i) => (
                      <div key={i} className="bg-gray-50 rounded p-1 border text-xs">
                        <p className="font-medium text-gray-500 truncate">{src.fileName}</p>
                        <p className="line-clamp-2 text-gray-400 mt-0.5">{src.chunkText}</p>
                      </div>
                    ))}
                  </div>
                </details>
              )}
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="p-3 border-t bg-white flex gap-2">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
          placeholder="Nhập câu hỏi..."
          disabled={loading}
          className="flex-1 border rounded-lg px-3 py-2 text-sm outline-none
                     focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
        />
        <button
          onClick={handleSend}
          disabled={loading || !input.trim()}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm
                     font-medium hover:bg-blue-700
                     disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Gửi
        </button>
      </div>
    </div>
  );
}
