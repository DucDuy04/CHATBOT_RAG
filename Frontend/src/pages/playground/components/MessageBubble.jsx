import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import SourcePills from "./SourcePills";

/**
 * MessageBubble — renders a single chat message.
 * User messages: blue bubble on right.
 * Assistant messages: white bubble on left with markdown + source pills.
 *
 * Props:
 *   message       — { id, role, content, streaming, sources, latency }
 *   selectedSource — currently highlighted source or null
 *   onSourceClick  — (source | null) => void
 */
export default function MessageBubble({ message, selectedSource, onSourceClick }) {
  const isUser = message.role === "user";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      {isUser ? (
        /* User bubble */
        <div className="max-w-[75%] bg-blue-600 text-white rounded-2xl rounded-br-sm px-4 py-3 text-sm">
          <p className="whitespace-pre-wrap break-words" style={{ overflowWrap: "anywhere" }}>
            {message.content}
          </p>
        </div>
      ) : (
        /* Assistant bubble */
        <div className="w-full">
          <div className="bg-white border text-gray-800 rounded-2xl rounded-bl-sm px-4 py-3 text-sm overflow-hidden">
            <div className="prose prose-sm max-w-none prose-p:leading-relaxed prose-pre:p-0">
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  table: ({ ...props }) => (
                    <div className="my-2 overflow-x-auto">
                      <table
                        className="min-w-full text-sm border border-collapse border-gray-300"
                        {...props}
                      />
                    </div>
                  ),
                  th: ({ ...props }) => (
                    <th
                      className="px-3 py-2 font-semibold text-left text-gray-700 bg-gray-100 border border-gray-300"
                      {...props}
                    />
                  ),
                  td: ({ ...props }) => (
                    <td
                      className="px-3 py-2 text-gray-600 border border-gray-300"
                      {...props}
                    />
                  ),
                  p: ({ ...props }) => (
                    <p
                      className="mb-2 break-words last:mb-0"
                      style={{ overflowWrap: "anywhere" }}
                      {...props}
                    />
                  ),
                }}
              >
                {message.content || ""}
              </ReactMarkdown>

              {/* Blinking cursor while streaming */}
              {message.streaming && (
                <span className="inline-block w-1.5 h-4 bg-gray-400 ml-0.5 animate-pulse align-middle rounded-sm" />
              )}
            </div>
          </div>

          {/* Source pills below bot message (only when done streaming) */}
          {!message.streaming && message.sources && message.sources.length > 0 && (
            <SourcePills
              sources={message.sources}
              selectedSource={selectedSource}
              onPillClick={onSourceClick}
            />
          )}
        </div>
      )}
    </div>
  );
}
