(function(){"use strict";(function(){const o=window.RagChatbotConfig||{};o.apiUrl,o.title;const t=document.createElement("div");t.id="rag-chatbot-bubble",t.innerHTML=`
    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <path d="M20 2H4C2.9 2 2 2.9 2 4v18l4-4h14c1.1 0
               2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
    </svg>
  `,document.body.appendChild(t);const e=document.createElement("iframe");e.id="rag-chatbot-frame",e.src=`${o.frontendUrl||"http://localhost:5173"}/widget`,e.classList.add("hidden"),document.body.appendChild(e);let n=!1;t.addEventListener("click",()=>{n=!n,e.classList.toggle("hidden",!n),t.innerHTML=n?`<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
           <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41
                    10.59 12 5 17.59 6.41 19 12 13.41 17.59 19
                    19 17.59 13.41 12z"/>
         </svg>`:`<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
           <path d="M20 2H4C2.9 2 2 2.9 2 4v18l4-4h14c1.1 0
                    2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
         </svg>`})})()})();
