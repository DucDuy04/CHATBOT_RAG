// src/components/ChatWidget/ChatMessage.jsx
export const ChatMessage = ({ msg }) => {
    const isUser = msg.sender === 'user';
    return (
        <div className={`flex gap-2 ${isUser ? 'justify-end' : 'justify-start'}`}>
            {!isUser && (
                <div className="w-6 h-6 rounded-full bg-gray-300 flex-shrink-0 flex items-center justify-center mt-1">
                    <svg className="w-4 h-4 text-gray-600" fill="currentColor" viewBox="0 0 20 20"><path d="M10 2a2 2 0 012 2v2h2a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2V8a2 2 0 012-2h2V4a2 2 0 012-2zm0 2a2 2 0 00-2 2h4a2 2 0 00-2-2z"/></svg>
                </div>
            )}
            <div className="max-w-[75%]">
                <div className={`p-3 text-[14px] leading-relaxed whitespace-pre-wrap shadow-sm ${
                    isUser 
                    ? 'bg-blue-600 text-white rounded-2xl rounded-tr-sm' 
                    : 'bg-white text-gray-800 border border-gray-100 rounded-2xl rounded-tl-sm'
                }`}>
                    {msg.text}
                </div>
                <div className={`text-[10px] text-gray-400 mt-1 ${isUser ? 'text-right' : 'text-left'}`}>
                    {msg.time}
                </div>
            </div>
        </div>
    );
};