const OPTIONS = [
  { value: "all", label: "All" },
  { value: "positive", label: "Positive" },
  { value: "negative", label: "Negative" },
];

export default function FeedbackRatingFilter({ value, onChange }) {
  return (
    <div className="max-w-[220px]">
      <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">
        Rating filter
      </label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
      >
        {OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
}
