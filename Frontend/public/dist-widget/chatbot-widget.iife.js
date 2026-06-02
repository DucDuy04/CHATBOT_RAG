(function(){"use strict";(function(){f();const e=window.RagChatbotConfig||{},a=e.widgetKey||e.apiKey||"",s=e.position==="bottom-left"?"bottom-left":"bottom-right",r=p(e.widgetColor)?e.widgetColor:"#2563eb",c=m(e.launcherIcon),b=typeof e.welcomeMessage=="string"?e.welcomeMessage:"",o=document.createElement("div");o.id="rag-chatbot-bubble",o.style.background=r,h(o,s,24),o.innerHTML=w(c),document.body.appendChild(o);const n=document.createElement("iframe");n.id="rag-chatbot-frame";const d=`${e.frontendUrl||"http://localhost:5173"}/widget`,i=new URLSearchParams;a&&i.set("widgetKey",a),i.set("widgetColor",r),i.set("welcomeMessage",b),i.set("launcherIcon",c),n.src=i.toString()?`${d}?${i.toString()}`:d,h(n,s,24),n.classList.add("rag-chatbot-hidden"),document.body.appendChild(n),o.addEventListener("click",()=>{o.classList.add("rag-chatbot-hidden"),n.classList.remove("rag-chatbot-hidden")}),window.addEventListener("message",t=>{t?.data?.type==="RAG_CHATBOT_CLOSE"&&(n.classList.add("rag-chatbot-hidden"),o.classList.remove("rag-chatbot-hidden"))});function p(t){return typeof t=="string"&&/^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(t)}function m(t){return t==="help"||t==="spark"?t:"chat"}function h(t,g,l){t.style.left=g==="bottom-left"?`${l}px`:"auto",t.style.right=g==="bottom-right"?`${l}px`:"auto"}function w(t){return t==="help"?`<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48
                 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45
                 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41
                 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36
                 1.68-.93 2.25z"/>
      </svg>`:t==="spark"?`<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <path d="M7 2v11h3v9l7-12h-4l4-8z"/>
      </svg>`:`<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <path d="M20 2H4C2.9 2 2 2.9 2 4v18l4-4h14c1.1 0
               2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
    </svg>`}function f(){if(document.getElementById("rag-chatbot-widget-styles"))return;const t=document.createElement("style");t.id="rag-chatbot-widget-styles",t.textContent=`
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
    `,document.head.appendChild(t)}})()})();
