/**
 * toastSingleton — dùng trong axiosInstance (ngoài React tree).
 * ToastRegister component phải mount để register fn trước.
 */
let _toastFn = null;

export function registerToastFn(fn) {
  _toastFn = fn;
}

export const toastSingleton = {
  success: (msg) => _toastFn?.(msg, "success"),
  error:   (msg) => _toastFn?.(msg, "error"),
  warning: (msg) => _toastFn?.(msg, "warning"),
};
