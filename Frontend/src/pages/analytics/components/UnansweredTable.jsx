import EmptyState from "../../../components/common/EmptyState";
import SkeletonLoader from "../../../components/common/SkeletonLoader";

export default function UnansweredTable({ data = [], loading = false, error = null, onAddDocs }) {
  if (loading) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Unanswered questions</p>
        <SkeletonLoader variant="table" count={5} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-100 bg-red-50 p-5">
        <p className="text-sm font-semibold text-red-600">Unanswered questions</p>
        <p className="mt-2 text-sm text-red-500">{error}</p>
      </div>
    );
  }

  if (!data.length) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Unanswered questions</p>
        <EmptyState icon="❓" title="No unanswered questions" message="Great. No unanswered items in this sample." />
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="mb-4 text-sm font-semibold text-gray-700">Unanswered questions</p>
      <div className="overflow-x-auto">
        <table className="min-w-[720px] w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500">
              <th className="py-2 pr-3">Question</th>
              <th className="py-2 px-3">Chatbot</th>
              <th className="py-2 px-3">Count</th>
              <th className="py-2 pl-3 text-right">Add docs</th>
            </tr>
          </thead>
          <tbody>
            {data.map((item) => (
              <tr key={item.id} className="border-b border-gray-100 align-top">
                <td className="py-3 pr-3 text-gray-700">{item.question || "-"}</td>
                <td className="px-3 py-3 text-gray-600">{item.chatbotName || "-"}</td>
                <td className="px-3 py-3 text-gray-600">{item.count ?? "-"}</td>
                <td className="py-3 pl-3 text-right">
                  <button
                    type="button"
                    onClick={() => onAddDocs?.(item.question || "")}
                    className="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-50"
                  >
                    Add docs
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
