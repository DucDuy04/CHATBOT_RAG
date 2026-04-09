import { useState, useEffect } from 'react';
import { Plus } from 'lucide-react';
import ChatbotTable from '../components/chatbots/ChatbotTable';
import BotStatCard from '../components/chatbots/BotStatCard';
import { mockChatbotData } from '../mock/chatbotData';

export default function Chatbots() {
  const [data, setData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // Giả lập Fetch API
  useEffect(() => {
    const fetchData = async () => {
      setTimeout(() => {
        setData(mockChatbotData);
        setIsLoading(false);
      }, 500);
    };
    fetchData();
  },[]);

  if (isLoading) {
    return <div className="p-8 flex items-center justify-center h-full text-gray-500">Đang tải dữ liệu...</div>;
  }

  return (
    <div className="p-8 w-full max-w-7xl mx-auto">
      {/* Header Section */}
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Chatbot Management</h1>
          <p className="text-gray-500 text-base">
            Deploy and manage your RAG-powered AI assistants with specialized knowledge bases.
          </p>
        </div>
        <button className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2.5 rounded-full font-semibold text-sm flex items-center gap-2 shadow-sm transition-colors">
          <Plus size={18} strokeWidth={2.5} />
          Create Chatbot
        </button>
      </div>

      {/* Main Table */}
      <ChatbotTable chatbots={data.list} />

      {/* Bottom Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {data.stats.map((stat) => (
          <BotStatCard key={stat.id} {...stat} />
        ))}
      </div>
    </div>
  );
}