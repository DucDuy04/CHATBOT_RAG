import { Search, Bell } from 'lucide-react';

export default function TopHeader({ title }) {
  return (
    <div className="flex items-center justify-between pb-6 mb-6 border-b border-gray-100">
      <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
      
      <div className="flex items-center gap-6">
        {/* Search Bar */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
          <input 
            type="text" 
            placeholder="Search data..." 
            className="pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-full text-sm outline-none focus:border-blue-500 focus:bg-white transition-all w-64"
          />
        </div>

        {/* Notifications */}
        <button className="relative text-gray-500 hover:text-gray-700">
          <Bell size={20} />
          <span className="absolute top-0 right-0 w-2 h-2 bg-red-500 rounded-full border-2 border-white"></span>
        </button>

        {/* Profile */}
        <div className="flex items-center gap-3 cursor-pointer">
          <img src="https://api.dicebear.com/7.x/notionists/svg?seed=Alex" alt="Alex Chen" className="w-8 h-8 rounded-full bg-orange-100" />
          <span className="text-sm font-semibold text-gray-700">Alex Chen</span>
        </div>
      </div>
    </div>
  );
}