export default function ActivityTable({ activities }) {
  // Hàm phụ trợ để render màu cho Badge Status
  const getStatusBadge = (status) => {
    const styles = {
      'Success': 'bg-green-100 text-green-700',
      'Completed': 'bg-green-100 text-green-700',
      'Responded': 'bg-blue-100 text-blue-700',
      'Processing': 'bg-orange-100 text-orange-700'
    };
    return <span className={`px-2.5 py-1 text-[11px] font-semibold rounded-full ${styles[status] || 'bg-gray-100 text-gray-600'}`}>{status}</span>;
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm mt-6">
      <div className="flex items-center justify-between p-6 border-b border-gray-50">
        <h3 className="text-lg font-bold text-gray-900">Recent Activity</h3>
        <button className="text-blue-600 text-sm font-semibold hover:text-blue-700">View All</button>
      </div>
      
      <div className="w-full overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs text-gray-400 font-bold uppercase tracking-wider bg-gray-50/50">
            <tr>
              <th className="px-6 py-4">Event</th>
              <th className="px-6 py-4">Category</th>
              <th className="px-6 py-4">Status</th>
              <th className="px-6 py-4">Timestamp</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {activities.map((item) => (
              <tr key={item.id} className="hover:bg-gray-50/50 transition-colors">
                <td className="px-6 py-4 flex items-center gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${item.iconBg}`}>
                    <item.icon className={item.iconColor} size={16} />
                  </div>
                  <span className="font-semibold text-gray-800">{item.event}</span>
                </td>
                <td className="px-6 py-4 text-gray-500 font-medium">{item.category}</td>
                <td className="px-6 py-4">{getStatusBadge(item.status)}</td>
                <td className="px-6 py-4 text-gray-400 text-xs font-medium">{item.time}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}