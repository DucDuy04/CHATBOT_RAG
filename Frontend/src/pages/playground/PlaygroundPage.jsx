import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import { v4 as uuidv4 } from "uuid";
import { playgroundApi } from "../../api";
import { chatbotsApi } from "../../api";
import { useLayout } from "../../contexts/LayoutContext";
import { useToast } from "../../components/common/useToast";

import ChatbotSelector from "./components/ChatbotSelector";
import SessionList from "./components/SessionList";
import ChatWindow from "./components/ChatWindow";
import RetrievalPanel from "./components/RetrievalPanel";
import LatencyPanel from "./components/LatencyPanel";
import ModelOverridePanel from "./components/ModelOverridePanel";
import CompareModeToggle from "./components/CompareModeToggle";
import ComparePane from "./components/ComparePane";
import PromptBuilderDrawer from "./components/PromptBuilderDrawer";
import ExportSessionButton from "./components/ExportSessionButton";

const DEFAULT_OVERRIDE_PARAMS = { temperature: 0.7, topK: 5, maxTokens: 1024 };
const DEFAULT_COMPARE_CONFIG = {
  temperature: 0.7,
  topK: 5,
  maxTokens: 1024,
  systemPrompt: "",
};

/**
 * PlaygroundPage — /playground
 *
 * 3-column layout:
 *   Left  (md+): SessionList
 *   Center:      ChatWindow (streaming)
 *   Right (lg+): RetrievalPanel + LatencyPanel + ModelOverridePanel
 */
export default function PlaygroundPage() {
  const { setPageTitle, setRightSlot, clearRightSlot } = useLayout();
  const toast = useToast();

  // ── Chatbots ──────────────────────────────────────────────────────────────────
  const [chatbots, setChatbots] = useState([]);
  const [chatbotsLoading, setChatbotsLoading] = useState(true);
  const [selectedChatbotId, setSelectedChatbotId] = useState(null);

  // ── Sessions ──────────────────────────────────────────────────────────────────
  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [selectedSessionId, setSelectedSessionId] = useState(null);

  // ── Messages ──────────────────────────────────────────────────────────────────
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const abortControllerRef = useRef(null);

  // ── Right panel ────────────────────────────────────────────────────────────────
  const [lastSources, setLastSources] = useState([]);
  const [lastLatency, setLastLatency] = useState(null);
  const [selectedSource, setSelectedSource] = useState(null);
  const [overrideParams, setOverrideParams] = useState(DEFAULT_OVERRIDE_PARAMS);

  // ── Prompt Builder (session-only system prompt) ───────────────────────────────
  const [sessionPromptOverride, setSessionPromptOverride] = useState("");
  const [promptBuilderOpen, setPromptBuilderOpen] = useState(false);

  // ── Compare mode ─────────────────────────────────────────────────────────────
  const [compareMode, setCompareMode] = useState(false);
  const [compareInput, setCompareInput] = useState("");
  const [compareLoading, setCompareLoading] = useState(false);
  const [compareError, setCompareError] = useState(null);
  const [compareResult, setCompareResult] = useState(null);
  const [compareConfigA, setCompareConfigA] = useState({ ...DEFAULT_COMPARE_CONFIG });
  const [compareConfigB, setCompareConfigB] = useState({ ...DEFAULT_COMPARE_CONFIG });

  const chatOverrideParams = useMemo(() => {
    const o = { ...overrideParams };
    const sp = sessionPromptOverride.trim();
    if (sp) o.systemPrompt = sp;
    return o;
  }, [overrideParams, sessionPromptOverride]);

  // ── Helpers ────────────────────────────────────────────────────────────────────

  /** Abort any in-progress stream without touching other state. */
  const abortStream = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
  }, []);

  /** Load sessions for a given chatbot. */
  const loadSessions = useCallback(
    async (chatbotId) => {
      if (!chatbotId) {
        setSessions([]);
        return;
      }
      setSessionsLoading(true);
      try {
        const data = await playgroundApi.getSessions(chatbotId);
        setSessions(data || []);
      } catch {
        toast.error("Không thể tải sessions.");
        setSessions([]);
      } finally {
        setSessionsLoading(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );

  // ── Load chatbots on mount ─────────────────────────────────────────────────────
  const loadChatbots = useCallback(async () => {
    setChatbotsLoading(true);
    try {
      const res = await chatbotsApi.getChatbots({ page: 0, size: 100 });
      setChatbots(res.items || []);
    } catch {
      toast.error("Không thể tải danh sách chatbot.");
    } finally {
      setChatbotsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    loadChatbots();
  }, [loadChatbots]);

  // ── Abort stream on unmount ────────────────────────────────────────────────────
  useEffect(() => {
    return () => {
      abortStream();
    };
  }, [abortStream]);

  // ── Clear chat handler ─────────────────────────────────────────────────────────
  const handleClearChat = useCallback(() => {
    abortStream();
    setIsStreaming(false);
    setMessages([]);
    setLastSources([]);
    setLastLatency(null);
    setSelectedSource(null);
    setCompareResult(null);
    setCompareError(null);
    setCompareInput("");
  }, [abortStream]);

  // ── Set rightSlot "Clear chat" button ─────────────────────────────────────────
  useEffect(() => {
    setPageTitle("Playground");
    setRightSlot(
      <button
        onClick={handleClearChat}
        className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
      >
        Clear chat
      </button>
    );
    return () => clearRightSlot();
  }, [setPageTitle, setRightSlot, clearRightSlot, handleClearChat]);

  // ── Chatbot selection ──────────────────────────────────────────────────────────
  const handleChatbotChange = useCallback(
    (chatbotId) => {
      abortStream();
      setIsStreaming(false);
      setSelectedChatbotId(chatbotId);
      setSelectedSessionId(null);
      setMessages([]);
      setLastSources([]);
      setLastLatency(null);
      setSelectedSource(null);
      setSessionPromptOverride("");
      setCompareResult(null);
      setCompareError(null);
      setCompareInput("");
      setCompareConfigA({ ...DEFAULT_COMPARE_CONFIG });
      setCompareConfigB({ ...DEFAULT_COMPARE_CONFIG });
      loadSessions(chatbotId);
    },
    [abortStream, loadSessions]
  );

  // ── Session selection (restore messages) ──────────────────────────────────────
  const handleSessionSelect = useCallback(async (session) => {
    abortStream();
    setIsStreaming(false);
    setSelectedSessionId(session.id);
    setLastSources([]);
    setLastLatency(null);
    setSelectedSource(null);

    try {
      /* exportSession returns { messages } in mock mode.
         In real mode the response is a Blob, so result.messages will be
         undefined — graceful degradation to empty messages. */
      const result = await playgroundApi.exportSession(session.id);
      const restoredMsgs = Array.isArray(result?.messages)
        ? result.messages.map((m) => ({
            id: m.id || uuidv4(),
            role: m.role,
            content: m.content,
            streaming: false,
            sources: m.sources || [],
            latency: m.latency ?? null,
          }))
        : [];
      setMessages(restoredMsgs);

      // Restore right-panel sources from last assistant message
      const lastBot = [...restoredMsgs]
        .reverse()
        .find((m) => m.role === "assistant" && m.sources?.length > 0);
      if (lastBot?.sources) setLastSources(lastBot.sources);
    } catch {
      // Don't crash — just start with empty messages
      setMessages([]);
    }
  }, [abortStream]);

  // ── Session delete ─────────────────────────────────────────────────────────────
  const handleSessionDelete = useCallback(
    async (sessionId) => {
      try {
        await playgroundApi.deleteSession(sessionId);
        if (selectedSessionId === sessionId) {
          setSelectedSessionId(null);
          setMessages([]);
          setLastSources([]);
          setLastLatency(null);
        }
        await loadSessions(selectedChatbotId);
      } catch {
        toast.error("Không thể xóa session.");
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [selectedSessionId, selectedChatbotId, loadSessions]
  );

  // ── Send message ──────────────────────────────────────────────────────────────
  const handleSend = useCallback(() => {
    const trimmed = input.trim();
    if (!trimmed || isStreaming || !selectedChatbotId) return;

    const userMsgId = uuidv4();
    const botMsgId = uuidv4();

    setMessages((prev) => [
      ...prev,
      {
        id: userMsgId,
        role: "user",
        content: trimmed,
        streaming: false,
        sources: [],
        latency: null,
      },
      {
        id: botMsgId,
        role: "assistant",
        content: "",
        streaming: true,
        sources: [],
        latency: null,
      },
    ]);
    setInput("");
    setIsStreaming(true);
    setSelectedSource(null);

    const controller = playgroundApi.chat({
      chatbotId: selectedChatbotId,
      message: trimmed,
      sessionId: selectedSessionId || undefined,
      overrideParams: chatOverrideParams,

      onToken: (token) => {
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === botMsgId
              ? { ...msg, content: msg.content + token }
              : msg
          )
        );
      },

      onDone: (result) => {
        const sources = result?.sources || [];
        const latencyVal = result?.latency ?? null;
        const returnedSessionId = result?.sessionId || null;

        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === botMsgId
              ? { ...msg, streaming: false, sources, latency: latencyVal }
              : msg
          )
        );
        setLastSources(sources);
        setLastLatency(latencyVal);
        setIsStreaming(false);

        // Capture server-assigned session id if this was a new session
        if (returnedSessionId && !selectedSessionId) {
          setSelectedSessionId(returnedSessionId);
        }

        // Refresh session list in microtask to avoid setState-in-effect lint issue
        Promise.resolve().then(async () => {
          try {
            const data = await playgroundApi.getSessions(selectedChatbotId);
            setSessions(data || []);
          } catch {
            /* ignore refresh errors */
          }
        });
      },

      onError: (err) => {
        const errMsg = err?.message || "Có lỗi xảy ra, vui lòng thử lại.";
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === botMsgId
              ? { ...msg, content: errMsg, streaming: false }
              : msg
          )
        );
        setIsStreaming(false);
        toast.error(errMsg);
      },
    });

    abortControllerRef.current = controller;
  }, [input, isStreaming, selectedChatbotId, selectedSessionId, chatOverrideParams, toast]);

  // ── Compare (parallel A/B) ───────────────────────────────────────────────────
  const handleCompare = useCallback(async () => {
    const q = compareInput.trim();
    if (!selectedChatbotId || !q || isStreaming || compareLoading) return;

    setCompareLoading(true);
    setCompareError(null);
    setCompareResult(null);

    const globalSp = sessionPromptOverride.trim();
    const build = (cfg) => {
      const local = cfg.systemPrompt?.trim();
      const systemPrompt = local || globalSp;
      const out = {
        temperature: cfg.temperature,
        topK: cfg.topK,
        maxTokens: cfg.maxTokens,
      };
      if (systemPrompt) out.systemPrompt = systemPrompt;
      return out;
    };

    try {
      const data = await playgroundApi.compare({
        chatbotId: selectedChatbotId,
        message: q,
        configA: build(compareConfigA),
        configB: build(compareConfigB),
      });
      setCompareResult(data && typeof data === "object" ? data : null);
    } catch (e) {
      const msg = e?.message || "Compare thất bại.";
      setCompareError(msg);
      toast.error(msg);
    } finally {
      setCompareLoading(false);
    }
  }, [
    compareInput,
    selectedChatbotId,
    isStreaming,
    compareLoading,
    sessionPromptOverride,
    compareConfigA,
    compareConfigB,
    toast,
  ]);

  // ── Source click ──────────────────────────────────────────────────────────────
  const handleSourceClick = useCallback((source) => {
    setSelectedSource((prev) => {
      if (!source) return null;
      if (
        prev?.fileName === source.fileName &&
        prev?.sectionTitle === source.sectionTitle
      ) {
        return null; // toggle off
      }
      return source;
    });
  }, []);

  // ── Render ────────────────────────────────────────────────────────────────────
  return (
    <div className="flex flex-col h-full min-h-0">

      {/* ── Top bar: chatbot selector + playground tools ── */}
      <div className="shrink-0 flex items-center gap-3 px-4 py-2.5 bg-white border-b flex-wrap gap-y-2">
        <ChatbotSelector
          chatbots={chatbots}
          selectedId={selectedChatbotId}
          onChange={handleChatbotChange}
          loading={chatbotsLoading}
        />
        {selectedSessionId && (
          <span className="text-xs text-gray-400 bg-gray-100 px-2 py-1 rounded-full font-mono">
            Session: {selectedSessionId.slice(-8)}
          </span>
        )}
        <div className="flex items-center gap-2 flex-wrap w-full md:w-auto md:ml-auto">
          <CompareModeToggle
            enabled={compareMode}
            onChange={setCompareMode}
            disabled={isStreaming}
          />
          <button
            type="button"
            onClick={() => setPromptBuilderOpen(true)}
            className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            Prompt Builder
          </button>
          <ExportSessionButton sessionId={selectedSessionId} toast={toast} />
        </div>
      </div>

      <PromptBuilderDrawer
        isOpen={promptBuilderOpen}
        onClose={() => setPromptBuilderOpen(false)}
        appliedText={sessionPromptOverride}
        onApply={(text) => setSessionPromptOverride((text || "").trim())}
        onClearOverride={() => setSessionPromptOverride("")}
      />

      {/* ── Main 3-column layout ── */}
      <div className="flex flex-1 min-h-0 overflow-hidden">

        {/* Left: Session list (hidden on mobile) */}
        <div className="hidden md:flex w-56 shrink-0 flex-col border-r bg-white overflow-hidden">
          <SessionList
            sessions={sessions}
            selectedSessionId={selectedSessionId}
            onSelect={handleSessionSelect}
            onDelete={handleSessionDelete}
            loading={sessionsLoading}
            chatbotSelected={!!selectedChatbotId}
          />
        </div>

        {/* Center: core chat OR compare pane (messages preserved when toggling) */}
        <div className="flex-1 min-w-0 flex flex-col min-h-0 overflow-hidden">
          {compareMode ? (
            <ComparePane
              compareInput={compareInput}
              onCompareInputChange={setCompareInput}
              compareConfigA={compareConfigA}
              compareConfigB={compareConfigB}
              onCompareConfigAChange={setCompareConfigA}
              onCompareConfigBChange={setCompareConfigB}
              compareLoading={compareLoading}
              compareError={compareError}
              compareResult={compareResult}
              onRunCompare={handleCompare}
              runDisabled={
                !selectedChatbotId ||
                !compareInput.trim() ||
                isStreaming ||
                compareLoading
              }
              globalPromptActive={!!sessionPromptOverride.trim()}
            />
          ) : (
            <ChatWindow
              messages={messages}
              isStreaming={isStreaming}
              input={input}
              onInputChange={setInput}
              onSend={handleSend}
              disabled={!selectedChatbotId}
              selectedSource={selectedSource}
              onSourceClick={handleSourceClick}
            />
          )}
        </div>

        {/* Right: Retrieval + Latency + Model overrides (hidden below lg) */}
        <div className="hidden lg:flex w-72 shrink-0 flex-col border-l bg-white overflow-y-auto">
          <div className="border-b">
            <RetrievalPanel
              sources={lastSources}
              selectedSource={selectedSource}
              onSourceSelect={handleSourceClick}
            />
          </div>
          <div className="border-b">
            <LatencyPanel latency={lastLatency} />
          </div>
          <div>
            <ModelOverridePanel
              params={overrideParams}
              onChange={setOverrideParams}
            />
          </div>
        </div>

      </div>
    </div>
  );
}
