// function DashboardPage() {
//   return (
//     <section>
//       <h1>Dashboard</h1>
//       <p>Dashboard page placeholder.</p>
//     </section>
//   );
// }

// export default DashboardPage;

// import { useState, useEffect } from 'react';
// import TopHeader from '../components/common/TopHeader';
// import StatCard from '../components/dashboard/StatCard';
// import ActivityTable from '../components/dashboard/ActivityTable';
// import ChartWidget from '../components/dashboard/ChartWidget';
// import CtaWidget from '../components/dashboard/CtaWidget';

import { useState, useEffect } from 'react';
import TopHeader from '../components/common/TopHeader';
import StatCard from '../components/dashboard/StatCard';
import ActivityTable from '../components/dashboard/ActivityTable';
import ChartWidget from '../components/dashboard/ChartWidget';
import CtaWidget from '../components/dashboard/CtaWidget';

// Import Dữ liệu ảo
import { mockDashboardData } from '../mock/dashboardData';

export default function Dashboard() {
  const [dashboardData, setDashboardData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // Giả lập việc gọi API khi load trang
  useEffect(() => {
    const fetchDashboardData = async () => {
      // Trong môi trường thật, bạn sẽ thay thế bằng cuộc gọi API
      // Ví dụ: const response = await axios.get('/api/dashboard');
      // setDashboardData(response.data);

      setTimeout(() => {
        setDashboardData(mockDashboardData); // Sử dụng dữ liệu ảo
        setIsLoading(false);
      }, 500); // Fake delay 0.5s cho giống gọi API thật
    };

    fetchDashboardData();
  }, []); // [] đảm bảo useEffect chỉ chạy MỘT LẦN sau khi component mount

  if (isLoading) {
    return (
      <div className="p-8 w-full h-full flex items-center justify-center bg-[#f8f9fa]">
        <div className="flex flex-col items-center">
          <svg className="animate-spin h-8 w-8 text-blue-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          <p className="mt-4 text-gray-600">Đang tải dữ liệu Dashboard...</p>
        </div>
      </div>
    );
  }

  // Đảm bảo dashboardData có dữ liệu trước khi render các component con
  if (!dashboardData) {
    return (
      <div className="p-8 w-full h-full flex items-center justify-center bg-[#f8f9fa]">
        <p className="text-red-500">Không thể tải dữ liệu Dashboard.</p>
      </div>
    );
  }

  return (
    <div className="p-8 w-full h-full bg-[#f8f9fa] overflow-auto">
      {/* Top Header */}
      <TopHeader title="Dashboard" />

      {/* Stats Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-6">
        {dashboardData.stats.map((stat, index) => (
          <StatCard
            key={index}
            title={stat.title}
            value={stat.value}
            badge={stat.badge}
            badgeColor={stat.badgeColor}
            icon={stat.icon}
            iconColor={stat.iconColor}
            iconBg={stat.iconBg}
          />
        ))}
      </div>

      {/* Recent Activity Table */}
      <ActivityTable activities={dashboardData.activities} />

      {/* Bottom Section: Chart Widget & CTA Widget */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-6 pb-8">
        <ChartWidget />
        <CtaWidget />
      </div>
    </div>
  );
}