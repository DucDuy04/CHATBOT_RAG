import Modal from "./Modal";

/**
 * ConfirmDeleteModal — reusable destructive-action confirmation.
 * Props:
 *   isOpen, onClose, onConfirm, loading
 *   title, message, confirmText, cancelText
 */
export default function ConfirmDeleteModal({
  isOpen,
  onClose,
  onConfirm,
  loading = false,
  title    = "Xác nhận xóa",
  message  = "Bạn có chắc muốn xóa? Hành động này không thể hoàn tác.",
  confirmText = "Xóa",
  cancelText  = "Hủy",
}) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title} maxWidth="max-w-sm">
      <p className="text-sm text-gray-600 mb-6">{message}</p>

      <div className="flex justify-end gap-3">
        <button
          onClick={onClose}
          disabled={loading}
          className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-700
                     hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {cancelText}
        </button>
        <button
          onClick={onConfirm}
          disabled={loading}
          className="px-4 py-2 text-sm rounded-lg bg-red-600 text-white
                     hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed
                     flex items-center gap-2"
        >
          {loading && (
            <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
          )}
          {confirmText}
        </button>
      </div>
    </Modal>
  );
}
