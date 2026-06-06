import { useMemo, useState } from "react";
import { StatusBadge } from "../../../components/common";

function fallbackMask(value) {
  if (!value) return "—";
  if (value.length <= 10) return `${value.slice(0, 3)}****${value.slice(-2)}`;
  return `${value.slice(0, 8)}...${value.slice(-3)}`;
}

function formatDate(value) {
  if (!value) return "—";
  return new Date(value).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function ApiKeyRow({ item, onDelete }) {
  const [revealed, setRevealed] = useState(false);
  const hasRaw = Boolean(item?.plainTextKey || item?.rawKey || item?.key);

  const masked = useMemo(() => item?.maskedKey || fallbackMask(item?.plainTextKey || item?.rawKey || item?.key), [item]);
  const revealedValue = item?.plainTextKey || item?.rawKey || item?.key || masked;

  return (
    <div className="rounded-lg border border-gray-200 p-4">
      <div className="mb-2 flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-gray-800">{item?.name || "API Key"}</p>
        <StatusBadge status={(item?.status || "inactive").toLowerCase()} />
      </div>

      <p className="rounded bg-gray-50 px-2 py-1 font-mono text-xs text-gray-700 select-all break-all">
        {revealed ? revealedValue : masked}
      </p>

      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-500">
        <span>Created: {formatDate(item?.createdAt)}</span>
        <span>Last used: {formatDate(item?.lastUsedAt)}</span>
      </div>

      <div className="mt-3 flex items-center gap-2">
        <button
          type="button"
          onClick={() => setRevealed((prev) => !prev)}
          className="rounded border border-gray-300 px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50"
        >
          {revealed ? "Hide" : "Reveal"}
        </button>
        {!hasRaw && <span className="text-xs text-amber-700">Raw key is unavailable in current API response.</span>}
        <button
          type="button"
          onClick={() => onDelete(item)}
          className="ml-auto rounded border border-red-200 px-2.5 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
        >
          Delete
        </button>
      </div>
    </div>
  );
}
