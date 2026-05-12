import { useEffect } from "react";
import { createPortal } from "react-dom";

/**
 * Drawer — slide-in từ right.
 * - Escape close
 * - Click overlay close
 */
export default function Drawer({
  isOpen,
  onClose,
  title,
  children,
  width = "w-96",
}) {
  // Escape close
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [isOpen, onClose]);

  // Block body scroll
  useEffect(() => {
    document.body.style.overflow = isOpen ? "hidden" : "";
    return () => { document.body.style.overflow = ""; };
  }, [isOpen]);

  return createPortal(
    <>
      {/* Overlay */}
      <div
        className={`
          fixed inset-0 z-40 bg-black/40
          transition-opacity duration-200
          ${isOpen ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"}
        `}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Drawer panel */}
      <div
        className={`
          fixed top-0 right-0 z-50 h-full ${width} bg-white shadow-xl
          flex flex-col
          transition-transform duration-200 ease-in-out
          ${isOpen ? "translate-x-0" : "translate-x-full"}
        `}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b shrink-0 h-12">
          <h2 className="text-sm font-semibold text-gray-800">{title || ""}</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-xl leading-none"
            aria-label="Đóng"
          >
            ×
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-5">{children}</div>
      </div>
    </>,
    document.body
  );
}
