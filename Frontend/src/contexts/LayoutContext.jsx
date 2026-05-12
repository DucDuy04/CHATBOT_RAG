import { createContext, useContext, useState, useCallback } from "react";

/**
 * LayoutContext — cho phép page con set Header title và rightSlot action buttons.
 *
 * Cách dùng trong page:
 *   const { setRightSlot, clearRightSlot, setPageTitle } = useLayout();
 *   useEffect(() => {
 *     setRightSlot(<button onClick={...}>+ New</button>);
 *     return () => clearRightSlot();
 *   }, []);
 *
 * AppLayoutWithTitle sẽ:
 *   - Dùng contextTitle (nếu page đã gọi setPageTitle) thay vì route-default title
 *   - Reset context khi pathname thay đổi (route đổi)
 *   - Forward rightSlot vào AppLayout → Header
 */
const LayoutContext = createContext(null);

export function LayoutProvider({ children }) {
  const [pageTitle, setPageTitleState] = useState(null);
  const [rightSlot, setRightSlotState] = useState(null);

  const setPageTitle    = useCallback((title) => setPageTitleState(title), []);
  const setRightSlot    = useCallback((node)  => setRightSlotState(node),  []);
  const clearRightSlot  = useCallback(()      => setRightSlotState(null),  []);

  /** Xóa cả title override lẫn rightSlot — gọi khi route thay đổi */
  const resetLayout     = useCallback(() => {
    setPageTitleState(null);
    setRightSlotState(null);
  }, []);

  return (
    <LayoutContext.Provider
      value={{ pageTitle, setPageTitle, rightSlot, setRightSlot, clearRightSlot, resetLayout }}
    >
      {children}
    </LayoutContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useLayout() {
  const ctx = useContext(LayoutContext);
  if (!ctx) {
    throw new Error("useLayout() phải được gọi bên trong <LayoutProvider>.");
  }
  return ctx;
}
