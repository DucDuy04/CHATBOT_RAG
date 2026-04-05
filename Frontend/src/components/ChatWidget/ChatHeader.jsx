// src/components/ChatWidget/ChatHeader.jsx
export const ChatHeader = ({ onClose }) => {
    return (
        <div className="bg-blue-600 text-white px-4 py-3 flex items-center justify-between">
            <div className="flex items-center gap-3">
                <div className="relative">
                    <div className="w-10 h-10 bg-orange-200 rounded-full flex items-center justify-center overflow-hidden">
                        <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=Felix" alt="avatar" className="w-8 h-8"/>
                    </div>
                    <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-400 border-2 border-blue-600 rounded-full"></div>
                </div>
                <div>
                    <h3 className="font-semibold text-base">AI Assistant</h3>
                    <p className="text-blue-200 text-xs">Online</p>
                </div>
            </div>
            <div className="flex gap-2">
                <button onClick={onClose} className="hover:bg-blue-700 p-1 rounded-md transition">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M20 12H4"></path></svg>
                </button>
            </div>
        </div>
    );
};