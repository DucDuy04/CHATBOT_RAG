import { useState, forwardRef, useImperativeHandle } from "react";

const ORIGIN_PATTERN = /^https?:\/\/[a-zA-Z0-9.-]+(:\d+)?(\/.*)?$/;

/**
 * AllowedOriginsInput — tag-style input for CORS allowed origins.
 *
 * Props:
 *   origins  : string[]  — current list
 *   onChange : (origins: string[]) => void
 *
 * Ref API (for save / snippet sync):
 *   getOriginsForSave() — commits pending text field as a tag if valid; returns list to PUT.
 */
const AllowedOriginsInput = forwardRef(function AllowedOriginsInput(
  { origins, onChange },
  ref
) {
  const [inputValue, setInputValue] = useState("");
  const [error, setError] = useState("");

  function addOrigin(raw) {
    const value = raw.trim();
    if (!value) return;

    if (!ORIGIN_PATTERN.test(value)) {
      setError("Invalid origin. Example: https://example.com or http://localhost:3000");
      return;
    }
    if (origins.includes(value)) {
      setError("This origin is already in the list.");
      return;
    }

    setError("");
    setInputValue("");
    onChange([...origins, value]);
  }

  function removeOrigin(origin) {
    onChange(origins.filter((o) => o !== origin));
  }

  useImperativeHandle(
    ref,
    () => ({
      getOriginsForSave() {
        const v = inputValue.trim();
        if (!v) {
          return origins;
        }
        if (!ORIGIN_PATTERN.test(v)) {
          setError("Invalid origin. Example: https://example.com or http://localhost:3000");
          return origins;
        }
        if (origins.includes(v)) {
          setInputValue("");
          setError("");
          return origins;
        }
        const next = [...origins, v];
        setInputValue("");
        setError("");
        onChange(next);
        return next;
      },
    }),
    [inputValue, origins, onChange]
  );

  function handleKeyDown(e) {
    if (e.key === "Enter") {
      e.preventDefault();
      addOrigin(inputValue);
    }
  }

  function handleBlur() {
    const v = inputValue.trim();
    if (!v) return;
    if (ORIGIN_PATTERN.test(v) && !origins.includes(v)) {
      setError("");
      setInputValue("");
      onChange([...origins, v]);
    }
  }

  function handlePaste(e) {
    const text = e.clipboardData?.getData("text") || "";
    if (!text.includes("\n") && !text.includes("\r") && !text.includes(",")) {
      return;
    }
    e.preventDefault();
    const chunks = text
      .split(/[\n\r,]+/)
      .map((s) => s.trim())
      .filter(Boolean);
    if (chunks.length === 0) return;
    let next = [...origins];
    for (const chunk of chunks) {
      if (!ORIGIN_PATTERN.test(chunk)) continue;
      if (next.includes(chunk)) continue;
      next.push(chunk);
    }
    if (next.length !== origins.length) {
      setError("");
      onChange(next);
    }
  }

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-gray-700">
        Allowed Origins
      </label>

      {/* Tag list */}
      {origins.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {origins.map((origin) => (
            <span
              key={origin}
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs
                         font-medium bg-blue-50 text-blue-700 border border-blue-200"
            >
              {origin}
              <button
                type="button"
                onClick={() => removeOrigin(origin)}
                className="ml-0.5 text-blue-400 hover:text-blue-700 leading-none"
                aria-label={`Remove ${origin}`}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      {/* Input */}
      <input
        type="text"
        value={inputValue}
        onChange={(e) => {
          setInputValue(e.target.value);
          if (error) setError("");
        }}
        onBlur={handleBlur}
        onPaste={handlePaste}
        onKeyDown={handleKeyDown}
        placeholder="https://example.com"
        className={`w-full px-3 py-2 text-sm border rounded-lg bg-white
                    focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                    ${error ? "border-red-400" : "border-gray-300"}`}
      />

      {error && (
        <p className="text-xs text-red-600">{error}</p>
      )}

      <p className="text-xs text-gray-400">
        Press Enter to add an origin, or paste multiple lines / comma-separated URLs. Pending text is
        applied when you leave this field or click Save. Empty list clears allowed origins on save
        (snippet will show <code className="mx-0.5 rounded bg-gray-100 px-0.5">[]</code>).
      </p>
    </div>
  );
});

export default AllowedOriginsInput;
