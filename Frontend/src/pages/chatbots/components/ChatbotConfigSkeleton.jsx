import SkeletonLoader from "../../../components/common/SkeletonLoader";

/**
 * ChatbotConfigSkeleton — animated placeholder khi đang load chatbot config.
 * Mimics layout: tabs + 3 section cards.
 */
export default function ChatbotConfigSkeleton() {
  return (
    <div className="animate-pulse space-y-6">
      {/* Back link skeleton */}
      <div className="h-4 bg-gray-200 rounded w-32" />

      {/* Bot name skeleton */}
      <div className="h-6 bg-gray-200 rounded w-48" />

      {/* Tab bar skeleton */}
      <div className="flex gap-4 border-b border-gray-200 pb-0">
        <div className="h-4 bg-gray-200 rounded w-14 mb-2" />
        <div className="h-4 bg-gray-100 rounded w-12 mb-2" />
      </div>

      {/* Prompt section card */}
      <div className="rounded-xl border border-gray-200 bg-white p-5 space-y-3">
        <div className="h-4 bg-gray-200 rounded w-32" />
        <SkeletonLoader variant="line" count={4} />
        <div className="h-8 bg-gray-100 rounded w-24 mt-2" />
      </div>

      {/* Model section card */}
      <div className="rounded-xl border border-gray-200 bg-white p-5 space-y-3">
        <div className="h-4 bg-gray-200 rounded w-36" />
        <SkeletonLoader variant="line" count={3} />
        <div className="h-8 bg-gray-100 rounded w-24 mt-2" />
      </div>

      {/* Status section card */}
      <div className="rounded-xl border border-gray-200 bg-white p-5 space-y-2">
        <div className="h-4 bg-gray-200 rounded w-20" />
        <SkeletonLoader variant="line" count={2} />
        <div className="h-8 bg-gray-100 rounded w-28 mt-2" />
      </div>
    </div>
  );
}
