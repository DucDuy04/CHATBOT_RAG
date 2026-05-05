/**
 * StatusBadge — colored badge for entity status.
 * Variants: Active | Inactive | Warn | Info | Synced | Failed
 * Accepts status string (case-insensitive).
 */
const VARIANT_MAP = {
  active:   "bg-green-100  text-green-700  border-green-200",
  inactive: "bg-gray-100   text-gray-500   border-gray-200",
  warn:     "bg-amber-100  text-amber-700  border-amber-200",
  warning:  "bg-amber-100  text-amber-700  border-amber-200",
  info:     "bg-blue-100   text-blue-700   border-blue-200",
  synced:   "bg-teal-100   text-teal-700   border-teal-200",
  failed:   "bg-red-100    text-red-700    border-red-200",
  pending:  "bg-yellow-100 text-yellow-700 border-yellow-200",
  processing: "bg-purple-100 text-purple-700 border-purple-200",
  completed: "bg-green-100  text-green-700  border-green-200",
};

const LABELS = {
  active:     "Active",
  inactive:   "Inactive",
  warn:       "Warn",
  warning:    "Warn",
  info:       "Info",
  synced:     "Synced",
  failed:     "Failed",
  pending:    "Pending",
  processing: "Processing",
  completed:  "Completed",
};

export default function StatusBadge({ status, className = "" }) {
  const key = (status || "").toLowerCase();
  const styles = VARIANT_MAP[key] || "bg-gray-100 text-gray-500 border-gray-200";
  const label  = LABELS[key] || status || "—";

  return (
    <span
      className={`
        inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium
        border ${styles} ${className}
      `}
    >
      {label}
    </span>
  );
}
