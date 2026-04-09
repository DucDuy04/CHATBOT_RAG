export default function ChartWidget() {
  const bars =[40, 70, 50, 90, 100, 60]; // Phần trăm chiều cao cột
  
  return (
    <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm h-full flex flex-col">
      <h3 className="text-md font-bold text-gray-900 mb-6">Recent Queries Insight</h3>
      <div className="flex-1 bg-gray-50 rounded-xl border border-gray-100 border-dashed p-4 flex items-end justify-center gap-3 md:gap-6">
        {bars.map((height, index) => (
          <div 
            key={index} 
            className="w-8 md:w-10 bg-blue-400 rounded-t-md opacity-80 hover:opacity-100 transition-opacity"
            style={{ height: `${height}%`, backgroundColor: index === 4 ? '#2563eb' : '#8fb1fa' }}
          ></div>
        ))}
      </div>
    </div>
  );
}