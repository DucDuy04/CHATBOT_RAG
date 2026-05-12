import { Navigate, Outlet } from "react-router-dom";
import useAuthStore from "../stores/authStore";

/**
 * PrivateRoute — redirect về /login nếu không có token.
 * Route /widget không dùng wrapper này.
 */
export default function PrivateRoute() {
  const token = useAuthStore((s) => s.token);
  return token ? <Outlet /> : <Navigate to="/login" replace />;
}
