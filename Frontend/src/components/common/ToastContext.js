import { createContext } from "react";

/**
 * ToastContext — tách ra file riêng để tránh react-refresh/only-export-components.
 */
export const ToastContext = createContext(null);
