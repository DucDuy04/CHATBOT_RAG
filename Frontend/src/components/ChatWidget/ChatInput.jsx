// src/components/ChatWidget/ChatInput.jsx
import { useState } from 'react';

export const ChatInput = ({ onSendMessage, isLoading }) => {
    const [inputText, setInputText] = useState("");

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!inputText.trim() || isLoading) return;
        onSendMessage(inputText);
        setInputText(""); // Xóa input sau khi gửi
    };

    return (
        <div className="bg-white p-3 border-t border-gray-100">
            <form onSubmit={handleSubmit} className="flex items-center gap-2 bg-gray-50 border border-gray-200 rounded-full p-1 shadow-inner">
                <button type="button" className="p-2 text-gray-400 hover:text-gray-600">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"></path></svg>
                </button>
                <input 
                    type="text" 
                    placeholder="Type a message..." 
                    className="flex-1 bg-transparent outline-none text-gray-700 placeholder-gray-400"
                    value={inputText}
                    onChange={(e) => setInputText(e.target.value)}
                    disabled={isLoading}
                />
                <button 
                    type="submit" 
                    disabled={!inputText.trim() || isLoading}
                    className="bg-blue-600 text-white p-2 rounded-full hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"></path></svg>
                </button>
            </form>
            <div className="text-center text-[10px] text-gray-400 mt-2 font-medium flex items-center justify-center gap-1">
                POWERED BY <span className="w-3 h-3 bg-gray-300 inline-block rounded-sm"></span> GeniusAI
            </div>
        </div>
    );
};