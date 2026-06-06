import { useState, useEffect, useCallback, useContext, useRef } from "react";
import { createPortal } from "react-dom";
import { registerToastFn } from "./toastSingleton";
import { ToastContext } from "./ToastContext";

// ─── Provider ─────────────────────────────────────────────────────────────────

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const counterRef = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback((message, variant = "info", duration = 3000) => {
    const id = ++counterRef.current;
    setToasts((prev) => [...prev, { id, message, variant }]);
    if (duration > 0) {
      setTimeout(() => dismiss(id), duration);
    }
    return id;
  }, [dismiss]);

  return (
    <ToastContext.Provider value={toast}>
      {children}
      {createPortal(
        <ToastContainer toasts={toasts} onDismiss={dismiss} />,
        document.body
      )}
    </ToastContext.Provider>
  );
}

// ─── ToastRegister: expose toast fn ra ngoài React tree (axiosInstance) ───────

export function ToastRegister() {
  const ctx = useContext(ToastContext);
  useEffect(() => {
    if (ctx) registerToastFn(ctx);
    return () => registerToastFn(null);
  }, [ctx]);
  return null;
}

// ─── ToastContainer ───────────────────────────────────────────────────────────

function ToastContainer({ toasts, onDismiss }) {
  return (
    <div
      aria-live="polite"
      className="fixed top-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none"
    >
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

// ─── ToastItem ────────────────────────────────────────────────────────────────

const VARIANTS = {
  success: "bg-green-600 text-white",
  error:   "bg-red-600   text-white",
  warning: "bg-amber-500 text-white",
  info:    "bg-blue-600  text-white",
};

const ICONS = {
  success: "✓",
  error:   "✕",
  warning: "⚠",
  info:    "ℹ",
};

function ToastItem({ toast, onDismiss }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setVisible(true), 10);
    return () => clearTimeout(t);
  }, []);

  return (
    <div
      className={`
        pointer-events-auto flex items-start gap-3 px-4 py-3 rounded-lg shadow-lg
        min-w-[260px] max-w-[360px] text-sm font-medium
        transition-all duration-200
        ${VARIANTS[toast.variant] || VARIANTS.info}
        ${visible ? "opacity-100 translate-x-0" : "opacity-0 translate-x-4"}
      `}
    >
      <span className="text-base leading-none mt-0.5 shrink-0">
        {ICONS[toast.variant] || ICONS.info}
      </span>
      <span className="flex-1 break-words">{toast.message}</span>
      <button
        onClick={() => onDismiss(toast.id)}
        className="shrink-0 opacity-70 hover:opacity-100 text-lg leading-none"
        aria-label="Đóng thông báo"
      >
        ×
      </button>
    </div>
  );
}
