import { useState, useRef, useEffect } from "react";
import { v4 as uuidv4 } from "uuid";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

const API_URL = import.meta.env.VITE_API_URL;

const getSessionId = () => {
  const stored = localStorage.getItem("chat_session_id");
  if (stored) return stored;
  const newId = uuidv4();
  localStorage.setItem("chat_session_id", newId);
  return newId;
};

export default function ChatPage() {
  const [messages, setMessages] = useState([
    {
      id: "welcome",
      role: "assistant",
      content: "Xin chào! Tôi là trợ lý AI. Hãy đặt câu hỏi về tài liệu bạn đã upload.",
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
        body: JSON.stringify({
          sessionId,
          message: userMessage.content,
        }),
      });

      if (!response.ok) throw new Error("Lỗi kết nối server");

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

          // FIX: KHÔNG dùng .trim() cho eventData
          // .trim() sẽ xóa mất token là khoảng trắng " " → các từ bị dính nhau
          const eventData = dataLine.startsWith("data: ")
            ? dataLine.slice(6)   // bỏ "data: " (6 ký tự, giữ nguyên nội dung)
            : dataLine.slice(5);  // bỏ "data:"  (5 ký tự, không có space)

         if (eventName === "token") {
            await new Promise((resolve) => setTimeout(resolve, 30));
            let tokenText = eventData;
            try {
                // ✅ Parse JSON để giữ nguyên space
                tokenText = JSON.parse(eventData).token;
            } catch {
                tokenText = eventData;
            }

            setMessages((prev) =>
                prev.map((msg) => {
                    if (msg.id !== botMessageId) return msg;
                    return { ...msg, content: msg.content + tokenText };
                })
            );
        }else if (eventName === "done") {
            let sources = [];
            try {
              sources = JSON.parse(eventData);
            } catch {
              sources = [];
            }
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
      console.error("Lỗi stream:", error);
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

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-screen max-w-3xl mx-auto">

      {/* Header */}
      <div className="p-4 text-lg font-semibold bg-white border-b">
        RAG Chatbot
      </div>

      {/* Danh sách tin nhắn */}
      <div className="flex-1 p-4 space-y-4 overflow-y-auto bg-gray-50">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
          >
            {/* FIX: thêm overflow-hidden để tránh bubble bị tràn */}
            <div
              className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm overflow-hidden
                ${msg.role === "user"
                  ? "bg-blue-600 text-white rounded-br-sm"
                  : "bg-white border text-gray-800 rounded-bl-sm"}`}
            >
             <div className="text-sm prose max-w-none prose-p:leading-relaxed prose-pre:p-0">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                // Customize lại thẻ table để hiển thị đẹp bằng Tailwind
                table: ({ node, ...props }) => (
                  <div className="my-3 overflow-x-auto">
                    <table className="min-w-full text-sm border border-collapse border-gray-300" {...props} />
                  </div>
                ),
                th: ({ node, ...props }) => (
                  <th className="px-3 py-2 font-semibold text-left text-gray-700 bg-gray-100 border border-gray-300" {...props} />
                ),
                td: ({ node, ...props }) => (
                  <td className="px-3 py-2 text-gray-600 border border-gray-300" {...props} />
                ),
                p: ({ node, ...props }) => (
                  <p className="mb-2 break-words whitespace-pre-wrap last:mb-0" style={{ overflowWrap: "anywhere" }} {...props} />
                )
              }}
            >
              {msg.content}
            </ReactMarkdown>
            
            {/* Con trỏ nhấp nháy khi đang stream */}
            {msg.streaming && (
              <span className="inline-block w-1.5 h-4 bg-gray-500 ml-1 animate-pulse align-middle rounded-sm" />
            )}
          </div>

              {!msg.streaming && msg.sources && msg.sources.length > 0 && (
                <details className="mt-2 text-xs text-gray-500">
                  <summary className="cursor-pointer hover:text-gray-700">
                    Nguồn ({msg.sources.length})
                  </summary>
                  <div className="mt-1 space-y-1">
                    {msg.sources.map((src, i) => (
                      <div key={i} className="p-2 border rounded bg-gray-50">
                        <p
                          className="font-medium text-gray-600 break-words"
                          style={{ overflowWrap: "anywhere" }}
                        >
                          {src.fileName}
                        </p>
                        <p
                          className="mt-1 break-words line-clamp-2"
                          style={{ overflowWrap: "anywhere" }}
                        >
                          {src.chunkText}
                        </p>
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
      <div className="flex gap-2 p-4 bg-white border-t">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Nhập câu hỏi..."
          disabled={loading}
          className="flex-1 px-4 py-2 text-sm border outline-none rounded-xl focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
        />
        <button
          onClick={handleSend}
          disabled={loading || !input.trim()}
          className="px-5 py-2 text-sm font-medium text-white bg-blue-600 rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? "Đang trả lời..." : "Gửi"}
        </button>
      </div>
    </div>
  );
}
