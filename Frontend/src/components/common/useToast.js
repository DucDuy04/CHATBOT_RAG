import { useContext } from "react";
import { ToastContext } from "./ToastContext";

/**
 * useToast — hook dùng trong React components.
 * Phải dùng bên trong ToastProvider.
 */
export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside <ToastProvider>");

  return {
    success: (msg, duration) => ctx(msg, "success", duration),
    error:   (msg, duration) => ctx(msg, "error",   duration),
    warning: (msg, duration) => ctx(msg, "warning", duration),
    info:    (msg, duration) => ctx(msg, "info",    duration),
  };
}
