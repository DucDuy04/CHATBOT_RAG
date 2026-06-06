import { useState } from "react";
import { ConfirmDeleteModal, useToast } from "../../../components/common";

export default function DangerZoneSection() {
  const toast = useToast();
  const [open, setOpen] = useState(false);

  const handleConfirm = () => {
    setOpen(false);
    toast.warning("Delete account is not implemented yet.");
  };

  return (
    <section className="rounded-xl border border-red-200 bg-red-50 p-5 shadow-sm">
      <p className="text-sm font-semibold text-red-700">Danger Zone</p>
      <p className="mt-1 text-sm text-red-600">
        Deleting account is irreversible and will remove access to settings data.
      </p>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="mt-4 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
      >
        Delete account
      </button>

      <ConfirmDeleteModal
        isOpen={open}
        onClose={() => setOpen(false)}
        onConfirm={handleConfirm}
        title="Delete account"
        message="Delete account flow is not connected to backend yet. Continue?"
        confirmText="Confirm"
        cancelText="Cancel"
      />
    </section>
  );
}
