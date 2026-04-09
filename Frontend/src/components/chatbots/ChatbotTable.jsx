import { Paperclip, Pencil, Trash2, ChevronLeft, ChevronRight } from 'lucide-react';

export default function ChatbotTable({ chatbots }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden mb-6">
      {/* Table Header */}
      <div className="grid grid-cols-12 gap-4 p-5 border-b border-gray-100 bg-white text-sm font-semibold text-gray-900">
        <div className="col-span-4 pl-2">Name</div>
        <div className="col-span-3">Documents Linked</div>
        <div className="col-span-3">Created Date</div>
        <div className="col-span-2 text-right pr-2">Actions</div>
      </div>

      {/* Table Body */}
      <div className="divide-y divide-gray-100">
        {chatbots.map((bot) => (
          <div key={bot.id} className="grid grid-cols-12 gap-4 p-5 items-center hover:bg-gray-50 transition-colors">
            {/* Name & Icon */}
            <div className="col-span-4 flex items-center gap-4">
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${bot.iconBg}`}>
                <bot.icon className={bot.iconColor} size={20} />
              </div>
              <span className="font-semibold text-gray-900 text-base">{bot.name}</span>
            </div>

            {/* Documents Linked (Badge) */}
            <div className="col-span-3 flex items-center">
              <span className="inline-flex items-center gap-1.5 bg-gray-100 text-gray-600 px-3 py-1.5 rounded-full text-xs font-semibold">
                <Paperclip size={14} />
                {bot.docsLinked} docs
              </span>
            </div>

            {/* Created Date */}
            <div className="col-span-3 text-sm text-gray-500 font-medium">
              {bot.createdDate}
            </div>

            {/* Actions */}
            <div className="col-span-2 flex items-center justify-end gap-3 text-gray-400">
              <button className="hover:text-blue-600 transition-colors p-1">
                <Pencil size={18} />
              </button>
              <button className="hover:text-red-600 transition-colors p-1">
                <Trash2 size={18} />
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Table Footer / Pagination */}
      <div className="p-5 border-t border-gray-100 flex items-center justify-between bg-white">
        <span className="text-sm text-gray-500 font-medium">
          Showing 2 of 12 chatbots
        </span>
        <div className="flex items-center gap-2">
          <button className="w-8 h-8 flex items-center justify-center rounded-full border border-gray-200 text-gray-500 hover:bg-gray-50">
            <ChevronLeft size={16} />
          </button>
          <button className="w-8 h-8 flex items-center justify-center rounded-full bg-blue-600 text-white font-medium text-sm">
            1
          </button>
          <button className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 text-gray-700 font-medium text-sm">
            2
          </button>
          <button className="w-8 h-8 flex items-center justify-center rounded-full border border-gray-200 text-gray-500 hover:bg-gray-50">
            <ChevronRight size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}