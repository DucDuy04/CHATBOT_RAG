import { useState, useEffect, useCallback, useRef } from "react";
import { dashboardApi } from "../../api";
import { useLayout } from "../../contexts/LayoutContext";
import MetricCard from "./components/MetricCard";
import MessageVolumeChart from "./components/MessageVolumeChart";
import TopChatbotsList from "./components/TopChatbotsList";
import ActivityTable from "./components/ActivityTable";

/**
 * DashboardPage — trang tổng quan.
 *
 * Gọi song song 4 API:
 *   - dashboardApi.getSummary()         → MetricCards x4
 *   - dashboardApi.getMessageVolume(7)  → MessageVolumeChart
 *   - dashboardApi.getTopChatbots(5)    → TopChatbotsList
 *   - dashboardApi.getActivity(20)      → ActivityTable
 *
 * Header rightSlot: nút Refresh — disabled khi đang load/refresh.
 * Mỗi vùng có loading state và error state riêng.
 * Dùng Promise.allSettled nên một API fail không crash toàn page.
 */
export default function DashboardPage() {
  const { setRightSlot, clearRightSlot } = useLayout();

  // ─── Per-section state ─────────────────────────────────────────────────────

  const [summary, setSummary]               = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError]     = useState(null);

  const [volume, setVolume]               = useState(null);
  const [volumeLoading, setVolumeLoading] = useState(true);
  const [volumeError, setVolumeError]     = useState(null);

  const [topChatbots, setTopChatbots]       = useState(null);
  const [topLoading, setTopLoading]         = useState(true);
  const [topError, setTopError]             = useState(null);

  const [activity, setActivity]             = useState(null);
  const [activityLoading, setActivityLoading] = useState(true);
  const [activityError, setActivityError]   = useState(null);

  const [isRefreshing, setIsRefreshing] = useState(false);

  // Ref để ngăn duplicate request khi user click Refresh liên tục
  const inFlightRef = useRef(false);

  // ─── loadDashboard ─────────────────────────────────────────────────────────

  const loadDashboard = useCallback(async () => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    setIsRefreshing(true);

    // Clear errors, set loading
    setSummaryError(null);  setSummaryLoading(true);
    setVolumeError(null);   setVolumeLoading(true);
    setTopError(null);      setTopLoading(true);
    setActivityError(null); setActivityLoading(true);

    const [summaryRes, volumeRes, topRes, activityRes] = await Promise.allSettled([
      dashboardApi.getSummary(),
      dashboardApi.getMessageVolume(7),
      dashboardApi.getTopChatbots(5),
      dashboardApi.getActivity(20),
    ]);

    if (summaryRes.status === "fulfilled") {
      setSummary(summaryRes.value);
    } else {
      setSummaryError(summaryRes.reason?.message || "Failed to load summary");
    }
    setSummaryLoading(false);

    if (volumeRes.status === "fulfilled") {
      setVolume(volumeRes.value);
    } else {
      setVolumeError(volumeRes.reason?.message || "Failed to load message volume");
    }
    setVolumeLoading(false);

    if (topRes.status === "fulfilled") {
      setTopChatbots(topRes.value);
    } else {
      setTopError(topRes.reason?.message || "Failed to load top chatbots");
    }
    setTopLoading(false);

    if (activityRes.status === "fulfilled") {
      setActivity(activityRes.value);
    } else {
      setActivityError(activityRes.reason?.message || "Failed to load activity");
    }
    setActivityLoading(false);

    inFlightRef.current = false;
    setIsRefreshing(false);
  }, []);

  // ─── Initial load ──────────────────────────────────────────────────────────
  useEffect(() => {
    // loadDashboard là async — setState gọi sau khi API resolve, không cascade sync.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadDashboard();
  }, [loadDashboard]);

  // ─── Derived state for button ──────────────────────────────────────────────

  const isAnyLoading =
    summaryLoading || volumeLoading || topLoading || activityLoading || isRefreshing;

  // ─── Refresh button in Header rightSlot ───────────────────────────────────
  // Effect riêng cho update: không clear khi re-run (tránh flash).
  // Effect cleanup: xóa slot khi unmount.

  useEffect(() => {
    setRightSlot(
      <button
        onClick={loadDashboard}
        disabled={isAnyLoading}
        className={`
          inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-lg
          transition-colors
          ${isAnyLoading
            ? "bg-gray-100 text-gray-400 cursor-not-allowed"
            : "bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800"
          }
        `}
      >
        <span
          className={`text-base leading-none ${isRefreshing ? "animate-spin" : ""}`}
          aria-hidden="true"
        >
          ↻
        </span>
        {isRefreshing ? "Refreshing…" : "Refresh"}
      </button>
    );
  }, [setRightSlot, loadDashboard, isAnyLoading, isRefreshing]);

  useEffect(() => {
    return () => clearRightSlot();
  }, [clearRightSlot]);

  // ─── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="space-y-6 p-1">

      {/* ── MetricCards grid ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        <MetricCard
          title="Active Chatbots"
          value={summary?.activeChatbots}
          delta={summary?.activeChatbotsDelta}
          description="vs last period"
          loading={summaryLoading}
        />
        <MetricCard
          title="Messages / 7 days"
          value={summary?.messages7d}
          delta={summary?.messages7dDelta}
          description="vs last 7 days"
          loading={summaryLoading}
        />
        <MetricCard
          title="Documents"
          value={summary?.documentCount}
          delta={summary?.documentCountDelta}
          description="total indexed"
          loading={summaryLoading}
        />
      </div>

      {/* ── Summary error alert ───────────────────────────────────────────── */}
      {!summaryLoading && summaryError && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 flex items-center justify-between">
          <span className="text-sm text-red-600">{summaryError}</span>
          <button
            onClick={loadDashboard}
            className="text-xs text-red-700 font-medium hover:underline ml-4"
          >
            Retry
          </button>
        </div>
      )}

      {/* ── Chart ────────────────────────────────────────────────────────── */}
      <MessageVolumeChart
        data={volume}
        loading={volumeLoading}
        error={volumeError}
      />

      {/* ── Bottom row: Top Chatbots + Activity ──────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Top Chatbots — 1/3 width on large screens */}
        <div className="lg:col-span-1">
          <TopChatbotsList
            data={topChatbots}
            loading={topLoading}
            error={topError}
            onRetry={loadDashboard}
          />
        </div>

        {/* Activity Table — 2/3 width on large screens */}
        <div className="lg:col-span-2">
          <ActivityTable
            data={activity}
            loading={activityLoading}
            error={activityError}
            onRetry={loadDashboard}
          />
        </div>
      </div>

    </div>
  );
}
