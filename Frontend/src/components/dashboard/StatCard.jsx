export default function StatCard({ title, value, badge, badgeColor, icon: Icon, iconColor, iconBg }) {
  return (
    <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm flex flex-col justify-between h-full">
      <div className="flex items-start justify-between mb-4">
        <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${iconBg}`}>
          <Icon className={iconColor} size={24} />
        </div>
        <span className={`px-2.5 py-1 text-[11px] font-semibold rounded-full ${badgeColor}`}>
          {badge}
        </span>
      </div>
      <div>
        <p className="text-sm text-gray-500 font-medium mb-1">{title}</p>
        <h3 className="text-3xl font-bold text-gray-900">{value}</h3>
      </div>
    </div>
  );
}