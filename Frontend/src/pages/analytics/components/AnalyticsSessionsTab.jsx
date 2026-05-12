import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { EmptyState, SkeletonLoader, useToast } from "../../../components/common";
import { analyticsApi } from "../../../api/analyticsApi";
import SessionsTable from "./SessionsTable";
import SessionsPagination from "./SessionsPagination";
import SessionDetailDrawer from "./SessionDetailDrawer";

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
  const toastRef = useRef(toast);
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
    toastRef.current = toast;
  }, [toast]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPage(0);
  }, [from, to, chatbotId]);

  const loadSessions = useCallback(async () => {
    if (!active || invalidRange) return;

    setState((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const payload = await analyticsApi.getSessions({
        from,
        to,
        chatbotId: chatbotId || undefined,
        page,
      });
      const normalized = normalizeSessionsResponse(payload);
      setState({ ...normalized, loading: false, error: null });
    } catch {
      setState((prev) => ({ ...prev, loading: false, error: "Failed to load sessions." }));
      toastRef.current.error("Failed to load sessions data.");
    }
  }, [active, chatbotId, from, invalidRange, page, to]);

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
        toastRef.current.error("Failed to load session conversation.");
        throw new Error("Failed to load session conversation.");
      }
    },
    []
  );

  const showEmpty = useMemo(() => !state.loading && !state.error && state.items.length === 0, [state]);

  return (
    <div className="space-y-4">
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
          <EmptyState
            icon="🗂️"
            title="No sessions found"
            message="Try changing the date range or chatbot filter."
          />
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
