import { useState } from "react";
import Modal from "../../../components/common/Modal";
import { useToast } from "../../../components/common/useToast";
import useChatbotStore from "../../../stores/chatbotStore";

const EMPTY_FORM = { name: "", description: "", domain: "" };

/**
 * CreateChatbotModal — modal tạo chatbot mới.
 *
 * Props:
 *   isOpen    — bật/tắt modal
 *   onClose   — callback đóng modal
 *   onCreated — callback sau khi tạo thành công (để page refresh list)
 */
export default function CreateChatbotModal({ isOpen, onClose, onCreated }) {
  const toast = useToast();
  const { creating, createChatbot } = useChatbotStore();

  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);

  function setField(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
    // Xóa lỗi field khi user bắt đầu sửa
    if (fieldErrors[key]) {
      setFieldErrors((e) => ({ ...e, [key]: undefined }));
    }
  }

  function validate() {
    const errors = {};
    if (!form.name.trim()) errors.name = "Name is required.";
    if (!form.domain.trim()) errors.domain = "Domain is required.";
    return errors;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    setSubmitError(null);

    try {
      await createChatbot({
        name: form.name.trim(),
        description: form.description.trim(),
        domain: form.domain.trim(),
      });
      toast.success(`Chatbot "${form.name.trim()}" created successfully!`);
      // Reset form trước khi đóng
      setForm(EMPTY_FORM);
      onClose();
      onCreated?.();
    } catch (err) {
      setSubmitError(err?.message || "Failed to create chatbot. Please try again.");
    }
  }

  function handleClose() {
    if (creating) return; // Không cho đóng khi đang submit
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setSubmitError(null);
    onClose();
  }

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="New Chatbot" maxWidth="max-w-md">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">

        {/* Name */}
        <div>
          <label className="block text-xs font-semibold text-gray-600 mb-1">
            Name <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={form.name}
            onChange={(e) => setField("name", e.target.value)}
            placeholder="e.g. Customer Support Bot"
            autoFocus
            className={`w-full px-3 py-2 text-sm border rounded-lg
              focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
              ${fieldErrors.name ? "border-red-400 bg-red-50" : "border-gray-300"}`}
          />
          {fieldErrors.name && (
            <p className="text-xs text-red-500 mt-1">{fieldErrors.name}</p>
          )}
        </div>

        {/* Description */}
        <div>
          <label className="block text-xs font-semibold text-gray-600 mb-1">
            Description{" "}
            <span className="text-gray-400 font-normal">(optional)</span>
          </label>
          <textarea
            value={form.description}
            onChange={(e) => setField("description", e.target.value)}
            placeholder="Brief description of this chatbot's purpose..."
            rows={2}
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg
              focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
              resize-none"
          />
        </div>

        {/* Domain */}
        <div>
          <label className="block text-xs font-semibold text-gray-600 mb-1">
            Domain <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={form.domain}
            onChange={(e) => setField("domain", e.target.value)}
            placeholder="e.g. support, faq, technical"
            className={`w-full px-3 py-2 text-sm border rounded-lg
              focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
              ${fieldErrors.domain ? "border-red-400 bg-red-50" : "border-gray-300"}`}
          />
          {fieldErrors.domain && (
            <p className="text-xs text-red-500 mt-1">{fieldErrors.domain}</p>
          )}
          <p className="text-xs text-gray-400 mt-1">
            Dùng để phân loại chatbot (ví dụ: support, hr, legal).
          </p>
        </div>

        {/* Submit error */}
        {submitError && (
          <div className="text-sm text-red-600 bg-red-50 border border-red-100 px-3 py-2 rounded-lg">
            {submitError}
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-1">
          <button
            type="button"
            onClick={handleClose}
            disabled={creating}
            className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg
                       hover:bg-gray-50 transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={creating}
            className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg
                       hover:bg-blue-700 active:bg-blue-800 transition-colors
                       disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {creating ? "Creating…" : "Create Chatbot"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
