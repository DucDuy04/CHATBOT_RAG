import { playgroundApi } from "../../../api";

/**
 * ExportSessionButton — download playground session via playgroundApi.exportSession.
 * Handles Blob (real) and plain object (mock).
 *
 * Props:
 *   sessionId — string | null
 *   toast     — object with warning, error, success
 */
export default function ExportSessionButton({ sessionId, toast }) {
  const disabled = !sessionId;

  const handleClick = async () => {
    if (!sessionId) {
      toast.warning("No session selected to export.");
      return;
    }
    try {
      const result = await playgroundApi.exportSession(sessionId, { asBlob: true });

      let blob;
      let filename;

      if (result instanceof Blob) {
        blob = result;
        const ct = (blob.type || "").toLowerCase();
        const ext = ct.includes("csv") || ct.includes("text/csv") ? "csv" : "json";
        filename = `playground-session-${sessionId}.${ext}`;
      } else if (result != null && typeof result === "object") {
        blob = new Blob([JSON.stringify(result, null, 2)], { type: "application/json" });
        filename = `playground-session-${sessionId}.json`;
      } else {
        toast.error("Export: định dạng phản hồi không hợp lệ.");
        return;
      }

      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.success("Đã tải xuống export session.");
    } catch {
      toast.error("Export session thất bại.");
    }
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={disabled}
      title={disabled ? "Chọn một session trong danh sách trước khi export" : "Tải JSON/CSV session"}
      className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
    >
      Export log
    </button>
  );
}
