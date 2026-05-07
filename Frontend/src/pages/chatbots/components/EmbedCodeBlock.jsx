import { useState } from "react";
import { useToast } from "../../../components/common/useToast";

/**
 * EmbedCodeBlock — shows a read-only embed snippet and a Copy button.
 *
 * Props:
 *   snippet : string — the full embed code to display
 */
export default function EmbedCodeBlock({ snippet }) {
  const toast = useToast();
  const [copying, setCopying] = useState(false);

  async function handleCopy() {
    if (copying) return;
    setCopying(true);
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(snippet);
        toast.success("Embed snippet copied to clipboard!");
      } else {
        // Fallback: select text in a temporary textarea
        const el = document.createElement("textarea");
        el.value = snippet;
        el.style.position = "fixed";
        el.style.opacity = "0";
        document.body.appendChild(el);
        el.select();
        document.execCommand("copy");
        document.body.removeChild(el);
        toast.success("Embed snippet copied!");
      }
    } catch {
      toast.error("Failed to copy snippet. Please copy manually.");
    } finally {
      setCopying(false);
    }
  }

  return (
    <section className="rounded-xl border border-gray-200 bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-800">Embed Snippet</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            Paste this code into your website&apos;s{" "}
            <code className="text-xs bg-gray-100 px-1 rounded">&lt;body&gt;</code> tag.
          </p>
        </div>
        <button
          type="button"
          onClick={handleCopy}
          disabled={copying}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium
                     bg-gray-800 text-white rounded-lg hover:bg-gray-700 transition-colors
                     disabled:opacity-60 flex-shrink-0"
        >
          {copying ? "Copying…" : "📋 Copy"}
        </button>
      </div>
      <div className="p-4 overflow-x-auto">
        <pre className="text-xs text-gray-700 whitespace-pre font-mono leading-relaxed">
          {snippet}
        </pre>
      </div>
    </section>
  );
}
