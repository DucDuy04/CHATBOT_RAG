import "./widget.css";

(function () {
  injectWidgetStyles();

  // Lấy config từ website nhúng
  const config = window.RagChatbotConfig || {};
  const widgetKey = config.widgetKey || config.apiKey || "";
  const position = config.position === "bottom-left" ? "bottom-left" : "bottom-right";
  const widgetColor = isHexColor(config.widgetColor) ? config.widgetColor : "#2563eb";
  const launcherIcon = normalizeLauncherIcon(config.launcherIcon);
  const welcomeMessage = typeof config.welcomeMessage === "string"
    ? config.welcomeMessage
    : "";

  // Tạo nút bubble
  const bubble = document.createElement("div");
  bubble.id = "rag-chatbot-bubble";
  bubble.style.background = widgetColor;
  applyPositionStyles(bubble, position, 24);
  bubble.innerHTML = getLauncherSvg(launcherIcon);
  document.body.appendChild(bubble);

  // Tạo iframe chat
  const frame = document.createElement("iframe");
  frame.id  = "rag-chatbot-frame";
  const baseWidgetUrl = `${config.frontendUrl || "http://localhost:5173"}/widget`;
  const queryParams = new URLSearchParams();
  if (widgetKey) queryParams.set("widgetKey", widgetKey);
  queryParams.set("widgetColor", widgetColor);
  queryParams.set("welcomeMessage", welcomeMessage);
  queryParams.set("launcherIcon", launcherIcon);
  frame.src = queryParams.toString()
    ? `${baseWidgetUrl}?${queryParams.toString()}`
    : baseWidgetUrl;
  applyPositionStyles(frame, position, 24);
  frame.classList.add("rag-chatbot-hidden");
  document.body.appendChild(frame);

  // Click bubble để mở frame; bubble ẩn đi
  bubble.addEventListener("click", () => {
    bubble.classList.add("rag-chatbot-hidden");
    frame.classList.remove("rag-chatbot-hidden");
  });

  function isHexColor(value) {
    return typeof value === "string" && /^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(value);
  }

  function normalizeLauncherIcon(value) {
    return value === "help" || value === "spark" ? value : "chat";
  }

  function applyPositionStyles(element, side, offsetPx) {
    element.style.left = side === "bottom-left" ? `${offsetPx}px` : "auto";
    element.style.right = side === "bottom-right" ? `${offsetPx}px` : "auto";
  }

  function getLauncherSvg(iconName) {
    if (iconName === "help") {
      return `<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48
                 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45
                 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41
                 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36
                 1.68-.93 2.25z"/>
      </svg>`;
    }

    if (iconName === "spark") {
      return `<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <path d="M7 2v11h3v9l7-12h-4l4-8z"/>
      </svg>`;
    }

    return `<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <path d="M20 2H4C2.9 2 2 2.9 2 4v18l4-4h14c1.1 0
               2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
    </svg>`;
  }

  function injectWidgetStyles() {
    if (document.getElementById("rag-chatbot-widget-styles")) return;

    const style = document.createElement("style");
    style.id = "rag-chatbot-widget-styles";
    style.textContent = `
      #rag-chatbot-bubble {
        position: fixed;
        width: 56px;
        height: 56px;
        bottom: 24px;
        border-radius: 9999px;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.22);
        z-index: 2147483000;
        transition: transform 0.18s ease;
      }

      #rag-chatbot-bubble:hover {
        transform: scale(1.06);
      }

      #rag-chatbot-bubble svg {
        width: 24px;
        height: 24px;
        min-width: 24px;
        min-height: 24px;
        display: block;
      }

      #rag-chatbot-bubble svg path {
        fill: #fff;
      }

      #rag-chatbot-frame {
        position: fixed;
        width: min(360px, calc(100vw - 24px));
        height: min(520px, calc(100vh - 48px));
        bottom: 24px;
        border: 0;
        border-radius: 16px;
        box-shadow: 0 10px 36px rgba(0, 0, 0, 0.22);
        background: #fff;
        z-index: 2147482999;
      }

      .rag-chatbot-hidden {
        display: none !important;
      }
    `;
    document.head.appendChild(style);
  }
})();