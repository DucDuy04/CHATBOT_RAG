/**
 * CompareModeToggle — switches Playground between core chat and compare layout.
 *
 * Props:
 *   enabled  — boolean
 *   onChange — (boolean) => void
 *   disabled — optional, e.g. while streaming (optional guard at parent too)
 */
export default function CompareModeToggle({ enabled, onChange, disabled = false }) {
  return (
    <label
      className={`inline-flex items-center gap-2 text-sm select-none ${
        disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer"
      }`}
    >
      <input
        type="checkbox"
        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
        checked={enabled}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span className="font-medium text-gray-700">Compare mode</span>
    </label>
  );
}
