import { useCallback, useEffect, useMemo, useState } from "react";
import { EmptyState, SkeletonLoader, useToast } from "../../../components/common";
import { analyticsApi } from "../../../api/analyticsApi";
import SessionsTable from "./SessionsTable";
import SessionsPagination from "./SessionsPagination";
import SessionDetailDrawer from "./SessionDetailDrawer";

const RATING_OPTIONS = [
  { value: "", label: "All ratings" },
  { value: "positive", label: "Positive" },
  { value: "negative", label: "Negative" },
  { value: "unrated", label: "Unrated" },
];

function normalizeSessionsResponse(payload) {
  if (Array.isArray(payload)) {
    return { items: payload, page: 0, size: payload.length || 10, total: payload.length, totalPages: 1 };
  }

  const items = Array.isArray(payload?.items) ? payload.items : [];
  const page = Number(payload?.page) || 0;
  const size = Number(payload?.size) || 10;
  const total = Number(payload?.total) || items.length;
  const totalPages = Math.max(Number(payload?.totalPages) || Math.ceil(total / Math.max(size, 1)) || 1, 1);
  return { items, page, size, total, totalPages };
}

export default function AnalyticsSessionsTab({
  active,
  from,
  to,
  chatbotId,
  invalidRange,
}) {
  const toast = useToast();
  const [rating, setRating] = useState("");
  const [page, setPage] = useState(0);
  const [selectedSession, setSelectedSession] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [state, setState] = useState({
    items: [],
    page: 0,
    size: 10,
    total: 0,
    totalPages: 1,
    loading: false,
    error: null,
  });

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPage(0);
  }, [from, to, chatbotId, rating]);

  const loadSessions = useCallback(async () => {
    if (!active || invalidRange) return;

    setState((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const payload = await analyticsApi.getSessions({
        from,
        to,
        chatbotId: chatbotId || undefined,
        rating: rating || undefined,
        page,
      });
      const normalized = normalizeSessionsResponse(payload);
      setState({ ...normalized, loading: false, error: null });
    } catch {
      setState((prev) => ({ ...prev, loading: false, error: "Failed to load sessions." }));
      toast.error("Failed to load sessions data.");
    }
  }, [active, chatbotId, from, invalidRange, page, rating, to, toast]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadSessions();
  }, [loadSessions]);

  const handleView = (session) => {
    setSelectedSession(session);
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
  };

  const fetchMessages = useCallback(
    async (sessionId) => {
      try {
        return await analyticsApi.getSessionMessages(sessionId);
      } catch {
        toast.error("Failed to load session conversation.");
        throw new Error("Failed to load session conversation.");
      }
    },
    [toast]
  );

  const showEmpty = useMemo(() => !state.loading && !state.error && state.items.length === 0, [state]);

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <div className="max-w-[260px]">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Rating</label>
          <select
            value={rating}
            onChange={(e) => setRating(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
          >
            {RATING_OPTIONS.map((opt) => (
              <option key={opt.value || "all"} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {invalidRange && (
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-4 text-sm text-yellow-800">
          Sessions fetch is paused until the custom date range is valid.
        </div>
      )}

      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Sessions</p>

        {state.loading && <SkeletonLoader variant="table" count={6} />}

        {!state.loading && state.error && (
          <div className="rounded-lg border border-red-100 bg-red-50 p-4">
            <p className="text-sm text-red-600">{state.error}</p>
          </div>
        )}

        {showEmpty && (
          <EmptyState icon="🗂️" title="No sessions found" message="Try changing date range, chatbot or rating filter." />
        )}

        {!state.loading && !state.error && state.items.length > 0 && (
          <>
            <SessionsTable items={state.items} onView={handleView} />
            <SessionsPagination
              page={state.page}
              size={state.size}
              total={state.total}
              totalPages={state.totalPages}
              onPageChange={setPage}
            />
          </>
        )}
      </div>

      <SessionDetailDrawer
        isOpen={drawerOpen}
        selectedSession={selectedSession}
        fetchMessages={fetchMessages}
        onClose={closeDrawer}
      />
    </div>
  );
}
