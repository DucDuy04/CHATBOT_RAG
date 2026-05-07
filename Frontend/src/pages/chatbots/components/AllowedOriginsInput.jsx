import { useState } from "react";

const ORIGIN_PATTERN = /^https?:\/\/[a-zA-Z0-9.-]+(:\d+)?(\/.*)?$/;

/**
 * AllowedOriginsInput — tag-style input for CORS allowed origins.
 *
 * Props:
 *   origins  : string[]  — current list
 *   onChange : (origins: string[]) => void
 */
export default function AllowedOriginsInput({ origins, onChange }) {
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

  function handleKeyDown(e) {
    if (e.key === "Enter") {
      e.preventDefault();
      addOrigin(inputValue);
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
        Press Enter to add an origin. Leave empty to allow all (check backend policy).
      </p>
    </div>
  );
}
