export default function BotStatCard({ title, value, icon: Icon, iconBg, iconColor }) {
  return (
    <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm flex items-center gap-5">
      <div className={`w-14 h-14 rounded-full flex items-center justify-center ${iconBg}`}>
        <Icon className={iconColor} size={24} />
      </div>
      <div>
        <p className="text-sm text-gray-500 font-medium mb-1">{title}</p>
        <h3 className="text-2xl font-bold text-gray-900">{value}</h3>
      </div>
    </div>
  );
}