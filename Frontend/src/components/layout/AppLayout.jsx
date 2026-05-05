import { useState, useEffect } from "react";
import { Outlet } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";

/**
 * AppLayout — shell cho tất cả trang admin (private).
 *
 * Responsive logic:
 *   >1024px (lg) : sidebar 188px visible, không collapse
 *   768–1023px   : sidebar collapse icon-only (w-14)
 *   <768px (md)  : sidebar ẩn, hamburger trong header để toggle
 */
export default function AppLayout({ pageTitle, rightSlot }) {
  const [mobileOpen,   setMobileOpen]   = useState(false);
  const [tabletCollapsed, setTabletCollapsed] = useState(false);

  // Set initial collapse state based on viewport
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 1023px)");
    const mobileQ = window.matchMedia("(max-width: 767px)");

    const update = () => {
      if (mobileQ.matches) {
        setTabletCollapsed(false); // mobile: sidebar hidden via mobileOpen
      } else if (mq.matches) {
        setTabletCollapsed(true);  // tablet: icon-only
      } else {
        setTabletCollapsed(false); // desktop: full width
      }
    };

    update();
    mq.addEventListener("change", update);
    mobileQ.addEventListener("change", update);
    return () => {
      mq.removeEventListener("change", update);
      mobileQ.removeEventListener("change", update);
    };
  }, []);

  const isMobile = typeof window !== "undefined" && window.innerWidth < 768;
  const collapsed = !isMobile && tabletCollapsed;

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      {/* Sidebar — на мобильном скрыто через css, не через условие */}
      <div className={`${!mobileOpen ? "hidden md:block" : "block"}`}>
        <Sidebar
          collapsed={collapsed}
          onClose={() => setMobileOpen(false)}
        />
      </div>

      {/* Overlay для mobile когда sidebar открыт */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-20 bg-black/40 md:hidden"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Main area */}
      <div
        className={`
          flex flex-col flex-1 min-w-0 overflow-hidden
          transition-all duration-200
          md:${collapsed ? "ml-14" : "ml-[188px]"}
        `}
      >
        <Header
          pageTitle={pageTitle}
          rightSlot={rightSlot}
          onHamburger={() => setMobileOpen((v) => !v)}
        />

        {/* Content area — scrollable */}
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
