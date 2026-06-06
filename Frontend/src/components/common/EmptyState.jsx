/**
 * EmptyState — icon + message + optional CTA.
 * Props:
 *   icon      — emoji hoặc element
 *   title     — tiêu đề
 *   message   — mô tả
 *   action    — { label, onClick } hoặc ReactNode
 */
export default function EmptyState({ icon = "📭", title, message, action, className = "" }) {
  return (
    <div className={`flex flex-col items-center justify-center py-16 text-center ${className}`}>
      <span className="text-4xl mb-4">{icon}</span>

      {title && (
        <p className="text-sm font-semibold text-gray-700 mb-1">{title}</p>
      )}

      {message && (
        <p className="text-sm text-gray-400 max-w-xs">{message}</p>
      )}

      {action && (
        <div className="mt-5">
          {typeof action === "object" && action.label ? (
            <button
              onClick={action.onClick}
              className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              {action.label}
            </button>
          ) : (
            action
          )}
        </div>
      )}
    </div>
  );
}
