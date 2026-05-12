/**
 * WidgetLivePreview — admin-side preview of the embed widget.
 *
 * Renders a simulated website frame with a bubble launcher and
 * a mini chat window reflecting the current embed config in realtime.
 * Does NOT use widget.js or call any API.
 *
 * Props:
 *   widgetColor    : string  — hex color for bubble / header
 *   welcomeMessage : string
 *   position       : "bottom-right" | "bottom-left"
 *   launcherIcon   : "chat" | "help" | "spark"
 *   allowedOrigins : string[] optional — shown as count in preview footer
 */
export default function WidgetLivePreview({ widgetColor, welcomeMessage, position, launcherIcon, allowedOrigins = [] }) {
  const isRight = position !== "bottom-left";
  const alignClass = isRight ? "items-end" : "items-start";

  return (
    <section className="rounded-xl border border-gray-200 bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100">
        <h3 className="text-sm font-semibold text-gray-800">Live Preview</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Reflects current settings in real-time.
        </p>
      </div>

      {/* Simulated website frame */}
      <div className="relative bg-gray-50 border-t border-gray-100" style={{ minHeight: 280 }}>
        {/* Mock browser chrome */}
        <div className="flex items-center gap-1.5 px-3 py-2 bg-gray-200 border-b border-gray-300">
          <span className="w-2.5 h-2.5 rounded-full bg-red-400" />
          <span className="w-2.5 h-2.5 rounded-full bg-yellow-400" />
          <span className="w-2.5 h-2.5 rounded-full bg-green-400" />
          <div className="ml-2 flex-1 h-4 bg-white rounded text-xs text-gray-400 flex items-center px-2 overflow-hidden">
            https://your-website.com
          </div>
        </div>

        {/* Fake page content */}
        <div className="p-4 space-y-2 opacity-30 pointer-events-none select-none">
          <div className="h-3 bg-gray-400 rounded w-3/4" />
          <div className="h-3 bg-gray-300 rounded w-1/2" />
          <div className="h-3 bg-gray-300 rounded w-2/3" />
        </div>

        {/* Widget overlay — positioned inside preview box */}
        <div className={`absolute bottom-3 ${isRight ? "right-3" : "left-3"} flex flex-col ${alignClass} gap-2`}>
          {/* Mini chat window */}
          <div
            className="w-52 rounded-xl shadow-xl border border-gray-200 bg-white overflow-hidden"
            style={{ boxShadow: "0 8px 32px rgba(0,0,0,0.18)" }}
          >
            {/* Chat header */}
            <div
              className="px-3 py-2.5 flex items-center gap-2"
              style={{ background: widgetColor }}
            >
              <div className="w-5 h-5 rounded-full bg-white/30 flex items-center justify-center flex-shrink-0">
                <LauncherSvg icon={launcherIcon} color="white" size={12} />
              </div>
              <span className="text-xs font-semibold text-white truncate">Chat Support</span>
            </div>

            {/* Messages area */}
            <div className="p-2.5 space-y-2 bg-gray-50" style={{ minHeight: 80 }}>
              {/* Bot welcome bubble */}
              <div className="flex items-start gap-1.5">
                <div
                  className="w-5 h-5 rounded-full flex-shrink-0 flex items-center justify-center"
                  style={{ background: widgetColor }}
                >
                  <LauncherSvg icon={launcherIcon} color="white" size={9} />
                </div>
                <div className="bg-white rounded-lg rounded-tl-none px-2.5 py-1.5 text-xs text-gray-700
                                shadow-sm max-w-[150px]">
                  {welcomeMessage || "Xin chào! Tôi có thể giúp gì cho bạn?"}
                </div>
              </div>
            </div>

            {/* Input bar */}
            <div className="px-2 py-2 border-t border-gray-100 bg-white flex items-center gap-1.5">
              <div className="flex-1 h-6 bg-gray-100 rounded text-xs text-gray-400 px-2 flex items-center">
                Nhập tin nhắn…
              </div>
              <div
                className="w-6 h-6 rounded flex items-center justify-center flex-shrink-0"
                style={{ background: widgetColor }}
              >
                <svg viewBox="0 0 24 24" width="12" height="12" fill="white">
                  <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
                </svg>
              </div>
            </div>
          </div>

          {/* Bubble launcher */}
          <div
            className="w-11 h-11 rounded-full shadow-lg flex items-center justify-center
                       cursor-pointer hover:scale-105 transition-transform flex-shrink-0"
            style={{ background: widgetColor }}
          >
            <LauncherSvg icon={launcherIcon} color="white" size={22} />
          </div>
        </div>
      </div>

      {/* Position indicator */}
      <div className="px-5 py-3 border-t border-gray-100 bg-gray-50">
        <p className="text-xs text-gray-500">
          Position:{" "}
          <span className="font-medium text-gray-700">{position}</span>
          {" · "}Color:{" "}
          <span
            className="inline-block w-3 h-3 rounded-full border border-gray-300 align-middle mx-0.5"
            style={{ background: widgetColor }}
          />
          <span className="font-mono font-medium text-gray-700">{widgetColor}</span>
          {Array.isArray(allowedOrigins) && allowedOrigins.length > 0 && (
            <>
              {" · "}
              <span className="text-gray-600">{allowedOrigins.length} allowed origin(s)</span>
            </>
          )}
        </p>
      </div>
    </section>
  );
}

/** Inline SVG icon for launcher bubble — no external icon library. */
function LauncherSvg({ icon, color = "currentColor", size = 20 }) {
  if (icon === "help") {
    return (
      <svg viewBox="0 0 24 24" width={size} height={size} fill={color}>
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48
                 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45
                 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41
                 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36
                 1.68-.93 2.25z" />
      </svg>
    );
  }

  if (icon === "spark") {
    return (
      <svg viewBox="0 0 24 24" width={size} height={size} fill={color}>
        <path d="M7 2v11h3v9l7-12h-4l4-8z" />
      </svg>
    );
  }

  // Default: chat icon
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill={color}>
      <path d="M20 2H4C2.9 2 2 2.9 2 4v18l4-4h14c1.1 0
               2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z" />
    </svg>
  );
}
