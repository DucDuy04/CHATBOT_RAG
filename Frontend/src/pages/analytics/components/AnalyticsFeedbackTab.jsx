import { useMemo, useState } from "react";
import EmptyState from "../../../components/common/EmptyState";
import { feedbackMockData } from "../../../mocks/analyticsMock";
import FeedbackCommentsList from "./FeedbackCommentsList";
import FeedbackRatingFilter from "./FeedbackRatingFilter";
import FeedbackSummaryCards from "./FeedbackSummaryCards";

function normalizeFeedback(items) {
  if (!Array.isArray(items)) return [];
  return items.filter((item) => item && (item.rating === 1 || item.rating === -1));
}

export default function AnalyticsFeedbackTab() {
  const [ratingFilter, setRatingFilter] = useState("all");

  const allFeedback = useMemo(() => normalizeFeedback(feedbackMockData), []);
  const thumbsUp = useMemo(() => allFeedback.filter((item) => item.rating === 1).length, [allFeedback]);
  const thumbsDown = useMemo(() => allFeedback.filter((item) => item.rating === -1).length, [allFeedback]);

  const filteredComments = useMemo(() => {
    const withComment = allFeedback.filter((item) => (item.comment || "").trim().length > 0);
    if (ratingFilter === "positive") return withComment.filter((item) => item.rating === 1);
    if (ratingFilter === "negative") return withComment.filter((item) => item.rating === -1);
    return withComment;
  }, [allFeedback, ratingFilter]);

  if (!allFeedback.length) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <EmptyState
          icon="📝"
          title="Feedback data unavailable"
          message="No readable feedback dataset is available yet. A read endpoint is still needed for production analytics."
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
        Feedback tab is currently rendered from local mock data. No GET/read feedback endpoint is available in the current API contract.
      </div>

      <FeedbackSummaryCards thumbsUp={thumbsUp} thumbsDown={thumbsDown} />

      <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <FeedbackRatingFilter value={ratingFilter} onChange={setRatingFilter} />
      </div>

      <FeedbackCommentsList items={filteredComments} />

      <div className="rounded-xl border border-gray-200 bg-white p-4 text-sm text-gray-600 shadow-sm">
        POST endpoint <code>/api/chat/feedback</code> is available in API layer but is not triggered here because this analytics view does not provide authoritative message context for submitting new feedback.
      </div>
    </div>
  );
}
