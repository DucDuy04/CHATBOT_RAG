const PRESETS = [
  { key: "7d", label: "Last 7 days" },
  { key: "30d", label: "Last 30 days" },
  { key: "month", label: "This month" },
  { key: "custom", label: "Custom" },
];

export default function DateRangePicker({
  preset,
  from,
  to,
  onPresetChange,
  onFromChange,
  onToChange,
  invalidRange,
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
        <div className="min-w-[180px]">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">
            Range
          </label>
          <select
            value={preset}
            onChange={(e) => onPresetChange(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
          >
            {PRESETS.map((item) => (
              <option key={item.key} value={item.key}>
                {item.label}
              </option>
            ))}
          </select>
        </div>

        <div className="grid flex-1 grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">
              From
            </label>
            <input
              type="date"
              value={from}
              onChange={(e) => onFromChange(e.target.value)}
              disabled={preset !== "custom"}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">
              To
            </label>
            <input
              type="date"
              value={to}
              onChange={(e) => onToChange(e.target.value)}
              disabled={preset !== "custom"}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
        </div>
      </div>

      {invalidRange && (
        <p className="mt-2 text-sm text-red-600">Invalid date range: "from" must be before or equal to "to".</p>
      )}
    </div>
  );
}
