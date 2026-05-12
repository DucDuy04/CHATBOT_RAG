import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { analyticsApi } from "../../api/analyticsApi";
import { chatbotsApi } from "../../api/chatbotsApi";
import { useToast } from "../../components/common";
import { useLayout } from "../../contexts/LayoutContext";
import AnalyticsTabs from "./components/AnalyticsTabs";
import DateRangePicker from "./components/DateRangePicker";
import AnalyticsMetricCard from "./components/AnalyticsMetricCard";
import DailyBarChart from "./components/DailyBarChart";
import ChatbotShareBars from "./components/ChatbotShareBars";
import UnansweredTable from "./components/UnansweredTable";
import AnalyticsSessionsTab from "./components/AnalyticsSessionsTab";

function toIsoDate(dateObj) {
  const year = dateObj.getFullYear();
  const month = String(dateObj.getMonth() + 1).padStart(2, "0");
  const day = String(dateObj.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getPresetRange(preset) {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  if (preset === "7d") {
    const from = new Date(today);
    from.setDate(from.getDate() - 6);
    return { from: toIsoDate(from), to: toIsoDate(today) };
  }

  if (preset === "30d") {
    const from = new Date(today);
    from.setDate(from.getDate() - 29);
    return { from: toIsoDate(from), to: toIsoDate(today) };
  }

  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  return { from: toIsoDate(monthStart), to: toIsoDate(today) };
}

function escapeCsvField(value) {
  const str = value == null ? "" : String(value);
  if (/[",\n]/.test(str)) {
    return `"${str.replace(/"/g, "\"\"")}"`;
  }
  return str;
}

export default function AnalyticsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const toast = useToast();
  const { setRightSlot, clearRightSlot } = useLayout();
  const toastRef = useRef(toast);

  useEffect(() => {
    toastRef.current = toast;
  }, [toast]);

  const initialRange = useMemo(() => getPresetRange("7d"), []);
  const [activeTab, setActiveTab] = useState("usage");
  const [preset, setPreset] = useState("7d");
  const [from, setFrom] = useState(initialRange.from);
  const [to, setTo] = useState(initialRange.to);
  const [chatbotId, setChatbotId] = useState("");
  const [isExporting, setIsExporting] = useState(false);
  const [chatbotsLoading, setChatbotsLoading] = useState(true);
  const [chatbotOptions, setChatbotOptions] = useState([]);

  const [summaryState, setSummaryState] = useState({ data: null, loading: true, error: null });
  const [dailyState, setDailyState] = useState({ data: [], loading: true, error: null });
  const [byChatbotState, setByChatbotState] = useState({ data: [], loading: true, error: null });
  const [unansweredState, setUnansweredState] = useState({ data: [], loading: true, error: null });

  /** Feedback tab removed — old links `?activeTab=feedback` should not crash. */
  useEffect(() => {
    const raw = searchParams.get("activeTab") || searchParams.get("tab");
    if (raw !== "feedback") return;
    const next = new URLSearchParams(searchParams);
    next.delete("activeTab");
    next.delete("tab");
    setSearchParams(next, { replace: true });
    setActiveTab("usage");
  }, [searchParams, setSearchParams]);

  const handleAnalyticsTab = useCallback((key) => {
    if (key === "feedback") {
      setActiveTab("usage");
      return;
    }
    setActiveTab(key);
  }, []);

  const invalidRange = !from || !to || from > to;
  const hasLoadedData =
    summaryState.data ||
    (dailyState.data && dailyState.data.length > 0) ||
    (byChatbotState.data && byChatbotState.data.length > 0) ||
    (unansweredState.data && unansweredState.data.length > 0);

  const loadUsageData = useCallback(async () => {
    if (invalidRange) return;

    setSummaryState((prev) => ({ ...prev, loading: true, error: null }));
    setDailyState((prev) => ({ ...prev, loading: true, error: null }));
    setByChatbotState((prev) => ({ ...prev, loading: true, error: null }));
    setUnansweredState((prev) => ({ ...prev, loading: true, error: null }));

    const results = await Promise.allSettled([
      analyticsApi.getSummary({ from, to, chatbotId: chatbotId || undefined }),
      analyticsApi.getDaily({ from, to, chatbotId: chatbotId || undefined }),
      analyticsApi.getByChatbot({ from, to }),
      analyticsApi.getUnanswered({ limit: 10 }),
    ]);

    let failCount = 0;

    if (results[0].status === "fulfilled") {
      setSummaryState({ data: results[0].value, loading: false, error: null });
    } else {
      failCount += 1;
      setSummaryState({ data: null, loading: false, error: "Failed to load summary data." });
    }

    if (results[1].status === "fulfilled") {
      setDailyState({ data: Array.isArray(results[1].value) ? results[1].value : [], loading: false, error: null });
    } else {
      failCount += 1;
      setDailyState({ data: [], loading: false, error: "Failed to load daily chart data." });
    }

    if (results[2].status === "fulfilled") {
      setByChatbotState({ data: Array.isArray(results[2].value) ? results[2].value : [], loading: false, error: null });
    } else {
      failCount += 1;
      setByChatbotState({ data: [], loading: false, error: "Failed to load chatbot share data." });
    }

    if (results[3].status === "fulfilled") {
      setUnansweredState({ data: Array.isArray(results[3].value) ? results[3].value : [], loading: false, error: null });
    } else {
      failCount += 1;
      setUnansweredState({ data: [], loading: false, error: "Failed to load unanswered questions." });
    }

    if (failCount > 0) {
      toastRef.current.error(`${failCount} analytics widget(s) failed to load.`);
    }
  }, [chatbotId, from, invalidRange, to]);

  const handleExportCsv = useCallback(async () => {
    if (!hasLoadedData) {
      toastRef.current.warning("No analytics data to export yet.");
      return;
    }

    try {
      setIsExporting(true);

      const lines = [];
      lines.push("Summary");
      lines.push("Metric,Value,Delta");
      lines.push(`Total Messages,${summaryState.data?.totalMessages ?? ""},${summaryState.data?.totalMessagesDelta ?? ""}`);
      lines.push(`Unique Sessions,${summaryState.data?.uniqueSessions ?? ""},${summaryState.data?.uniqueSessionsDelta ?? ""}`);
      lines.push(`Satisfaction (%),${summaryState.data?.avgSatisfaction ?? ""},${summaryState.data?.avgSatisfactionDelta ?? ""}`);
      lines.push(`Fallback Rate (%),${summaryState.data?.fallbackRate ?? ""},${summaryState.data?.fallbackRateDelta ?? ""}`);
      lines.push("");

      lines.push("Daily");
      lines.push("Date,Messages,Sessions");
      dailyState.data.forEach((item) => {
        lines.push([item.date, item.messages, item.sessions].map(escapeCsvField).join(","));
      });
      lines.push("");

      lines.push("By Chatbot");
      lines.push("Chatbot,Message Count,Share (%)");
      byChatbotState.data.forEach((item) => {
        lines.push([item.chatbotName, item.messageCount, item.share].map(escapeCsvField).join(","));
      });
      lines.push("");

      lines.push("Unanswered");
      lines.push("Question,Chatbot,Count");
      unansweredState.data.forEach((item) => {
        lines.push([item.question, item.chatbotName, item.count].map(escapeCsvField).join(","));
      });

      const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `analytics-usage-${from}-to-${to}.csv`;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);

      toastRef.current.success("CSV exported successfully.");
    } catch {
      toastRef.current.error("Failed to export CSV.");
    } finally {
      setIsExporting(false);
    }
  }, [byChatbotState.data, dailyState.data, from, hasLoadedData, summaryState.data, to, unansweredState.data]);

  const exportRightSlot = useMemo(
    () => (
      <button
        type="button"
        onClick={handleExportCsv}
        disabled={!hasLoadedData || isExporting}
        className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isExporting ? "Exporting..." : "Export CSV"}
      </button>
    ),
    [handleExportCsv, hasLoadedData, isExporting]
  );

  useEffect(() => {
    setRightSlot(exportRightSlot);
  }, [exportRightSlot, setRightSlot]);

  useEffect(() => {
    return () => clearRightSlot();
  }, [clearRightSlot]);

  useEffect(() => {
    let active = true;

    async function loadChatbots() {
      setChatbotsLoading(true);
      try {
        const res = await chatbotsApi.getChatbots({ page: 0, size: 100 });
        if (!active) return;
        setChatbotOptions(Array.isArray(res?.items) ? res.items : []);
      } catch {
        if (!active) return;
        setChatbotOptions([]);
        toastRef.current.error("Failed to load chatbot filter options.");
      } finally {
        if (active) setChatbotsLoading(false);
      }
    }

    loadChatbots();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (activeTab !== "usage") return;
    if (invalidRange) return;
    loadUsageData();
  }, [activeTab, invalidRange, loadUsageData]);

  const handlePresetChange = (nextPreset) => {
    setPreset(nextPreset);
    if (nextPreset === "custom") return;
    const range = getPresetRange(nextPreset);
    setFrom(range.from);
    setTo(range.to);
  };

  const handleAddDocs = (question) => {
    const query = question ? `?search=${encodeURIComponent(question)}` : "";
    navigate(`/documents${query}`);
  };

  const showSharedFilters = activeTab === "usage" || activeTab === "sessions";

  return (
    <div className="space-y-6">
      <AnalyticsTabs activeTab={activeTab} onChange={handleAnalyticsTab} />

      {showSharedFilters && (
        <>
          <DateRangePicker
            preset={preset}
            from={from}
            to={to}
            onPresetChange={handlePresetChange}
            onFromChange={setFrom}
            onToChange={setTo}
            invalidRange={invalidRange}
          />

          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Chatbot</label>
            <select
              value={chatbotId}
              onChange={(e) => setChatbotId(e.target.value)}
              disabled={chatbotsLoading}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none disabled:bg-gray-100"
            >
              <option value="">All chatbots</option>
              {chatbotOptions.map((bot) => (
                <option key={bot.id} value={bot.id}>
                  {bot.name}
                </option>
              ))}
            </select>
          </div>
        </>
      )}

      {activeTab === "usage" && (
        <>
          {invalidRange && (
            <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-4 text-sm text-yellow-800">
              Usage data fetch is paused until the custom date range is valid.
            </div>
          )}

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            <AnalyticsMetricCard
              title="Total messages"
              value={summaryState.data?.totalMessages}
              delta={summaryState.data?.totalMessagesDelta}
              loading={summaryState.loading}
              error={summaryState.error}
            />
            <AnalyticsMetricCard
              title="Unique sessions"
              value={summaryState.data?.uniqueSessions}
              delta={summaryState.data?.uniqueSessionsDelta}
              loading={summaryState.loading}
              error={summaryState.error}
            />
            <AnalyticsMetricCard
              title="Satisfaction"
              value={summaryState.data?.avgSatisfaction}
              valueSuffix="%"
              delta={summaryState.data?.avgSatisfactionDelta}
              loading={summaryState.loading}
              error={summaryState.error}
            />
            <AnalyticsMetricCard
              title="Fallback rate"
              value={summaryState.data?.fallbackRate}
              valueSuffix="%"
              delta={summaryState.data?.fallbackRateDelta}
              loading={summaryState.loading}
              error={summaryState.error}
            />
          </div>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <div className="xl:col-span-2">
              <DailyBarChart data={dailyState.data} loading={dailyState.loading} error={dailyState.error} />
            </div>
            <ChatbotShareBars
              data={byChatbotState.data}
              loading={byChatbotState.loading}
              error={byChatbotState.error}
            />
          </div>

          <UnansweredTable
            data={unansweredState.data}
            loading={unansweredState.loading}
            error={unansweredState.error}
            onAddDocs={handleAddDocs}
          />
        </>
      )}

      {activeTab === "sessions" && (
        <AnalyticsSessionsTab
          active={activeTab === "sessions"}
          from={from}
          to={to}
          chatbotId={chatbotId}
          invalidRange={invalidRange}
        />
      )}
    </div>
  );
}
