import AllowedOriginsInput from "./AllowedOriginsInput";

const HEX_RE = /^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/;

const POSITIONS = [
  { value: "bottom-right", label: "Bottom Right" },
  { value: "bottom-left",  label: "Bottom Left" },
];

const LAUNCHER_ICONS = [
  { value: "chat",  label: "Chat",  emoji: "💬" },
  { value: "help",  label: "Help",  emoji: "❓" },
  { value: "spark", label: "Spark", emoji: "⚡" },
];

const MAX_WELCOME_LEN = 200;

/**
 * EmbedSettingsSection — Widget Settings card.
 *
 * Props:
 *   form     : { widgetColor, welcomeMessage, position, launcherIcon, allowedOrigins }
 *   onChange : (key, value) => void
 *   onSave   : () => void
 *   saving   : boolean
 */
export default function EmbedSettingsSection({ form, onChange, onSave, saving }) {
  const hexError = form.widgetColor && !HEX_RE.test(form.widgetColor)
    ? "Invalid hex color (e.g. #2563eb)"
    : "";

  const isValid = !hexError && form.widgetColor;

  return (
    <section className="rounded-xl border border-gray-200 bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100">
        <h3 className="text-sm font-semibold text-gray-800">Widget Settings</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Configure the look and behavior of the embedded chatbot widget.
        </p>
      </div>

      <div className="px-5 py-5 space-y-6">
        {/* Widget Color */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-gray-700">Widget Color</label>
          <div className="flex items-center gap-2">
            <input
              type="color"
              value={HEX_RE.test(form.widgetColor) ? form.widgetColor : "#2563eb"}
              onChange={(e) => onChange("widgetColor", e.target.value)}
              className="h-9 w-12 p-0.5 rounded-lg border border-gray-300 cursor-pointer bg-white"
              title="Pick a color"
            />
            <input
              type="text"
              value={form.widgetColor}
              onChange={(e) => onChange("widgetColor", e.target.value)}
              placeholder="#2563eb"
              maxLength={7}
              className={`flex-1 px-3 py-2 text-sm border rounded-lg font-mono bg-white
                          focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                          ${hexError ? "border-red-400" : "border-gray-300"}`}
            />
          </div>
          {hexError && <p className="text-xs text-red-600">{hexError}</p>}
        </div>

        {/* Welcome Message */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="block text-sm font-medium text-gray-700">Welcome Message</label>
            <span className="text-xs text-gray-400">
              {form.welcomeMessage.length}/{MAX_WELCOME_LEN}
            </span>
          </div>
          <textarea
            value={form.welcomeMessage}
            onChange={(e) => onChange("welcomeMessage", e.target.value.slice(0, MAX_WELCOME_LEN))}
            rows={3}
            placeholder="Xin chào! Tôi có thể giúp gì cho bạn?"
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg bg-white
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                       resize-none"
          />
        </div>

        {/* Position */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-gray-700">Position</label>
          <select
            value={form.position}
            onChange={(e) => onChange("position", e.target.value)}
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg bg-white
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          >
            {POSITIONS.map((p) => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        </div>

        {/* Launcher Icon */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-gray-700">
            Launcher Icon
            <span className="ml-1 text-xs font-normal text-gray-400">(UI preview only)</span>
          </label>
          <div className="flex gap-2">
            {LAUNCHER_ICONS.map((icon) => (
              <button
                key={icon.value}
                type="button"
                onClick={() => onChange("launcherIcon", icon.value)}
                className={`flex flex-col items-center justify-center gap-1 w-16 h-16 rounded-xl border-2
                             text-xs font-medium transition-colors
                             ${form.launcherIcon === icon.value
                               ? "border-blue-500 bg-blue-50 text-blue-700"
                               : "border-gray-200 bg-white text-gray-600 hover:border-gray-300 hover:bg-gray-50"
                             }`}
              >
                <span className="text-xl leading-none">{icon.emoji}</span>
                <span>{icon.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Allowed Origins */}
        <AllowedOriginsInput
          origins={form.allowedOrigins}
          onChange={(v) => onChange("allowedOrigins", v)}
        />

        {/* Save button */}
        <div className="pt-2">
          <button
            type="button"
            onClick={onSave}
            disabled={!isValid || saving}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium
                       bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors
                       disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {saving ? (
              <>
                <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                Saving…
              </>
            ) : (
              "💾 Save Config"
            )}
          </button>
        </div>
      </div>
    </section>
  );
}
