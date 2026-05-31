import { useEffect, useMemo, useState } from "react";
import { settingsApi } from "../../../api/settingsApi";
import { SkeletonLoader, useToast } from "../../../components/common";

function getInitials(name, email) {
  const source = (name || "").trim() || (email || "").trim();
  if (!source) return "NA";
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  return source.slice(0, 2).toUpperCase();
}

export default function ProfileSettingsSection() {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [formError, setFormError] = useState(null);
  const [form, setForm] = useState({
    name: "",
    email: "",
    language: "vi",
    notifications: {
      embeddingFailed: true,
      dailySummary: true,
    },
  });

  const loadProfile = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await settingsApi.getProfile();
      setForm({
        name: data?.name || "",
        email: data?.email || "",
        language: data?.language || "vi",
        notifications: {
          embeddingFailed: data?.notifications?.embeddingFailed ?? true,
          dailySummary: data?.notifications?.dailySummary ?? true,
        },
      });
    } catch {
      setError("Failed to load profile settings.");
      toast.error("Failed to load profile settings.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProfile();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const initials = useMemo(() => getInitials(form.name, form.email), [form.email, form.name]);

  const handleToggle = (key) => {
    setForm((prev) => ({
      ...prev,
      notifications: {
        ...prev.notifications,
        [key]: !prev.notifications[key],
      },
    }));
  };

  const handleSave = async () => {
    setFormError(null);
    if (!form.name.trim() || !form.email.trim()) {
      setFormError("Full name and email are required.");
      return;
    }

    setSaving(true);
    try {
      const payload = {
        name: form.name.trim(),
        email: form.email.trim(),
        language: form.language,
        notifications: {
          embeddingFailed: !!form.notifications.embeddingFailed,
          dailySummary: !!form.notifications.dailySummary,
        },
      };
      await settingsApi.updateProfile(payload);
      toast.success("Profile updated.");
    } catch {
      setFormError("Failed to save profile.");
      toast.error("Failed to save profile.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-gray-700">Profile</p>
        <SkeletonLoader variant="line" count={8} />
      </section>
    );
  }

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="mb-4 text-sm font-semibold text-gray-700">Profile</p>

      {error && (
        <div className="mb-4 rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-600">
          <p>{error}</p>
          <button
            type="button"
            onClick={loadProfile}
            className="mt-2 rounded border border-red-200 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-100"
          >
            Retry
          </button>
        </div>
      )}

      <div className="mb-4 flex items-center gap-4">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-blue-100 text-lg font-semibold text-blue-700">
          {initials}
        </div>
        <div>
          <p className="text-sm font-medium text-gray-800">{form.name || form.email || "Profile"}</p>
          <p className="text-xs text-gray-500">Avatar initials are generated from your name.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Full name</label>
          <input
            type="text"
            value={form.name}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Email</label>
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm((prev) => ({ ...prev, email: e.target.value }))}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
          />
        </div>
      </div>

      <div className="mt-4 max-w-[260px]">
        <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Language</label>
        <select
          value={form.language}
          onChange={(e) => setForm((prev) => ({ ...prev, language: e.target.value }))}
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
        >
          <option value="vi">Tiếng Việt</option>
          <option value="en">English</option>
        </select>
      </div>

      <div className="mt-5 space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Notifications</p>
        <label className="flex items-center justify-between rounded-lg border border-gray-200 p-3 text-sm">
          <span>Embedding failed</span>
          <input
            type="checkbox"
            checked={!!form.notifications.embeddingFailed}
            onChange={() => handleToggle("embeddingFailed")}
          />
        </label>
        <label className="flex items-center justify-between rounded-lg border border-gray-200 p-3 text-sm">
          <span>Daily summary</span>
          <input
            type="checkbox"
            checked={!!form.notifications.dailySummary}
            onChange={() => handleToggle("dailySummary")}
          />
        </label>
      </div>

      {formError && <p className="mt-3 text-sm text-red-600">{formError}</p>}

      <div className="mt-4">
        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? "Saving..." : "Save profile"}
        </button>
      </div>
    </section>
  );
}
