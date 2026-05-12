const TABS = [
  { key: "usage", label: "Usage" },
  { key: "sessions", label: "Sessions" },
];

export default function AnalyticsTabs({ activeTab, onChange }) {
  return (
    <div className="border-b border-gray-200">
      <nav className="flex gap-2" aria-label="Analytics tabs">
        {TABS.map((tab) => {
          const active = tab.key === activeTab;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => onChange(tab.key)}
              className={[
                "px-4 py-2 text-sm font-medium border-b-2 transition-colors",
                active
                  ? "border-blue-600 text-blue-600"
                  : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300",
              ].join(" ")}
            >
              {tab.label}
            </button>
          );
        })}
      </nav>
    </div>
  );
}
