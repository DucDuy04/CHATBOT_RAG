/**
 * ChatbotEmbedSkeleton — loading placeholder for ChatbotEmbedPage.
 * Two-column layout: settings card (left) + preview panel (right).
 */
export default function ChatbotEmbedSkeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      {/* Back link placeholder */}
      <div className="h-4 bg-gray-200 rounded w-24" />

      {/* Bot name */}
      <div className="space-y-1.5">
        <div className="h-5 bg-gray-200 rounded w-48" />
        <div className="h-3 bg-gray-100 rounded w-24" />
      </div>

      {/* Tab bar */}
      <div className="flex border-b border-gray-200 gap-4">
        <div className="h-8 bg-gray-100 rounded w-16" />
        <div className="h-8 bg-gray-200 rounded w-16" />
      </div>

      {/* Main content: two columns */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Settings card */}
        <div className="space-y-5 rounded-xl border border-gray-200 bg-white p-5">
          <div className="h-4 bg-gray-200 rounded w-32" />
          {/* Color */}
          <div className="space-y-2">
            <div className="h-3 bg-gray-100 rounded w-20" />
            <div className="flex gap-2">
              <div className="h-9 w-12 bg-gray-200 rounded-lg" />
              <div className="h-9 bg-gray-100 rounded-lg flex-1" />
            </div>
          </div>
          {/* Welcome message */}
          <div className="space-y-2">
            <div className="h-3 bg-gray-100 rounded w-36" />
            <div className="h-20 bg-gray-100 rounded-lg" />
          </div>
          {/* Position */}
          <div className="space-y-2">
            <div className="h-3 bg-gray-100 rounded w-24" />
            <div className="h-9 bg-gray-100 rounded-lg" />
          </div>
          {/* Launcher icon */}
          <div className="space-y-2">
            <div className="h-3 bg-gray-100 rounded w-28" />
            <div className="flex gap-2">
              <div className="h-16 w-16 bg-gray-100 rounded-lg" />
              <div className="h-16 w-16 bg-gray-100 rounded-lg" />
              <div className="h-16 w-16 bg-gray-100 rounded-lg" />
            </div>
          </div>
          {/* Allowed origins */}
          <div className="space-y-2">
            <div className="h-3 bg-gray-100 rounded w-40" />
            <div className="h-9 bg-gray-100 rounded-lg" />
          </div>
          <div className="h-9 bg-gray-200 rounded-lg w-24" />
        </div>

        {/* Right panel: preview + code block */}
        <div className="space-y-5">
          {/* Preview card */}
          <div className="rounded-xl border border-gray-200 bg-white p-5 space-y-3">
            <div className="h-4 bg-gray-200 rounded w-28" />
            <div className="h-64 bg-gray-100 rounded-lg" />
          </div>
          {/* Code block card */}
          <div className="rounded-xl border border-gray-200 bg-white p-5 space-y-3">
            <div className="h-4 bg-gray-200 rounded w-36" />
            <div className="h-36 bg-gray-100 rounded-lg" />
          </div>
        </div>
      </div>
    </div>
  );
}
