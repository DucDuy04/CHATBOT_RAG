import { useEffect } from "react";
import { useLayout } from "../../contexts/LayoutContext";
import ProfileSettingsSection from "./components/ProfileSettingsSection";
import ApiKeysSection from "./components/ApiKeysSection";
import DangerZoneSection from "./components/DangerZoneSection";

export default function SettingsPage() {
  const { setPageTitle } = useLayout();

  useEffect(() => {
    setPageTitle("Settings");
  }, [setPageTitle]);

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6">
      <ProfileSettingsSection />
      <ApiKeysSection />
      <DangerZoneSection />
    </div>
  );
}
