import { useEffect, useState } from "react";
import { Drawer, EmptyState, SkeletonLoader } from "../../../components/common";

function SourcePill({ source }) {
  const fileName = source?.fileName || source?.documentName || source?.title || "Unknown source";
  const score = source?.score;
  const snippet = source?.snippet || source?.chunk || source?.content || "";

  return (
    <div className="rounded-md border border-gray-200 bg-gray-50 p-2">
      <div className="flex items-center justify-between gap-2 text-xs">
        <span className="truncate font-medium text-gray-700">{fileName}</span>
        {score != null && <span className="text-gray-500">score: {Number(score).toFixed(2)}</span>}
      </div>
      {snippet && <p className="mt-1 line-clamp-3 text-xs text-gray-500">{snippet}</p>}
    </div>
  );
}

export default function SessionDetailDrawer({
  isOpen,
  selectedSession,
  fetchMessages,
  onClose,
}) {
  const [state, setState] = useState({ loading: false, data: [], error: null });

  useEffect(() => {
    if (!isOpen || !selectedSession?.id) return;
    let active = true;

    async function load() {
      setState({ loading: true, data: [], error: null });
      try {
        const messages = await fetchMessages(selectedSession.id);
        if (!active) return;
        setState({ loading: false, data: Array.isArray(messages) ? messages : [], error: null });
      } catch {
        if (!active) return;
        setState({ loading: false, data: [], error: "Failed to load session messages." });
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [fetchMessages, isOpen, selectedSession?.id]);

  const handleRetry = async () => {
    if (!selectedSession?.id) return;
    setState({ loading: true, data: [], error: null });
    try {
      const messages = await fetchMessages(selectedSession.id);
      setState({ loading: false, data: Array.isArray(messages) ? messages : [], error: null });
    } catch {
      setState({ loading: false, data: [], error: "Failed to load session messages." });
    }
  };

  return (
    <Drawer
      isOpen={isOpen}
      onClose={onClose}
      title={selectedSession ? `Session ${selectedSession.id}` : "Session details"}
      width="w-full sm:w-[680px]"
    >
      {state.loading && <SkeletonLoader variant="line" count={8} />}

      {!state.loading && state.error && (
        <div className="rounded-lg border border-red-100 bg-red-50 p-4">
          <p className="text-sm font-semibold text-red-600">Session conversation</p>
          <p className="mt-1 text-sm text-red-500">{state.error}</p>
          <button
            type="button"
            onClick={handleRetry}
            className="mt-3 rounded-lg border border-red-200 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100"
          >
            Retry
          </button>
        </div>
      )}

      {!state.loading && !state.error && state.data.length === 0 && (
        <EmptyState
          icon="💬"
          title="No messages"
          message="This session has no conversation messages."
        />
      )}

      {!state.loading && !state.error && state.data.length > 0 && (
        <div className="space-y-4">
          {state.data.map((message, idx) => {
            const role = message.role === "assistant" ? "assistant" : "user";
            return (
              <div key={message.id || `${role}-${idx}`} className="space-y-2">
                <div
                  className={[
                    "max-w-[92%] rounded-xl px-4 py-3 text-sm",
                    role === "assistant"
                      ? "bg-gray-100 text-gray-800"
                      : "ml-auto bg-blue-600 text-white",
                  ].join(" ")}
                >
                  <p className="mb-1 text-xs font-semibold uppercase tracking-wide opacity-80">{role}</p>
                  <p className="whitespace-pre-wrap break-words">{message.content || "-"}</p>
                </div>

                {role === "assistant" && Array.isArray(message.sources) && message.sources.length > 0 && (
                  <div className="space-y-2 pl-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Sources</p>
                    {message.sources.map((source, sourceIdx) => (
                      <SourcePill key={`${source.fileName || "source"}-${sourceIdx}`} source={source} />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Drawer>
  );
}
