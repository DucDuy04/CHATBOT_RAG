import SkeletonLoader from "../../../components/common/SkeletonLoader";

/**
 * ChatbotSelector — dropdown to pick which chatbot to test in Playground.
 * Props:
 *   chatbots   — array of { id, name }
 *   selectedId — currently selected chatbot id or null
 *   onChange   — (chatbotId: string | null) => void
 *   loading    — bool, show skeleton while chatbots are loading
 */
export default function ChatbotSelector({ chatbots, selectedId, onChange, loading }) {
  if (loading) {
    return <SkeletonLoader variant="line" count={1} className="w-48 h-8" />;
  }

  return (
    <div className="flex items-center gap-2">
      <label className="text-sm text-gray-500 shrink-0 font-medium">Chatbot:</label>
      <select
        value={selectedId || ""}
        onChange={(e) => onChange(e.target.value || null)}
        className="text-sm border border-gray-300 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-[200px]"
      >
        <option value="">-- Chọn chatbot --</option>
        {chatbots.map((bot) => (
          <option key={bot.id} value={bot.id}>
            {bot.name}
          </option>
        ))}
      </select>
    </div>
  );
}
