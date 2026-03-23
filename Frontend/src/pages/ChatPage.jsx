import { useState, useRef, useEffect } from "react";
import { v4 as uuidv4 } from "uuid";
import { sendMessage } from "../api/chatApi";

// Lấy hoặc tạo sessionId, lưu vào localStorage
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

  // Tự scroll xuống khi có tin nhắn mới
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

    // Placeholder loading cho bot
    const loadingMessage = {
      id: "loading",
      role: "assistant",
      content: "",
      loading: true,
    };

    setMessages((prev) => [...prev, userMessage, loadingMessage]);
    setInput("");
    setLoading(true);

    try {
      const response = await sendMessage(sessionId, userMessage.content);

      // Thay placeholder bằng câu trả lời thật
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === "loading"
            ? {
                id: uuidv4(),
                role: "assistant",
                content: response.answer,
                sources: response.sources,
              }
            : msg
        )
      );
    } catch (error) {
      console.error("Lỗi gọi API chat:", error);
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === "loading"
            ? {
                id: uuidv4(),
                role: "assistant",
                content: "Có lỗi xảy ra, vui lòng thử lại.",
              }
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
      <div className="p-4 border-b font-semibold text-lg bg-white">
        RAG Chatbot
      </div>

      {/* Danh sách tin nhắn */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm
                ${msg.role === "user"
                  ? "bg-blue-600 text-white rounded-br-sm"
                  : "bg-white border text-gray-800 rounded-bl-sm"}`}
            >
              {/* Loading animation */}
              {msg.loading ? (
                <div className="flex gap-1 items-center h-5">
                  <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce [animation-delay:0ms]" />
                  <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce [animation-delay:150ms]" />
                  <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce [animation-delay:300ms]" />
                </div>
              ) : (
                <>
                  <p className="whitespace-pre-wrap">{msg.content}</p>

                  {/* Nguồn tài liệu */}
                  {msg.sources && msg.sources.length > 0 && (
                    <details className="mt-2 text-xs text-gray-500">
                      <summary className="cursor-pointer hover:text-gray-700">
                        Nguồn ({msg.sources.length})
                      </summary>
                      <div className="mt-1 space-y-1">
                        {msg.sources.map((src, i) => (
                          <div key={i} className="bg-gray-50 rounded p-2 border">
                            <p className="font-medium text-gray-600">{src.fileName}</p>
                            <p className="line-clamp-2 mt-1">{src.chunkText}</p>
                          </div>
                        ))}
                      </div>
                    </details>
                  )}
                </>
              )}
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      {/* Input gửi tin nhắn */}
      <div className="p-4 border-t bg-white flex gap-2">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Nhập câu hỏi..."
          disabled={loading}
          className="flex-1 border rounded-xl px-4 py-2 text-sm outline-none
                     focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
        />
        <button
          onClick={handleSend}
          disabled={loading || !input.trim()}
          className="bg-blue-600 text-white px-5 py-2 rounded-xl text-sm
                     font-medium hover:bg-blue-700
                     disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Gửi
        </button>
      </div>
    </div>
  );
}