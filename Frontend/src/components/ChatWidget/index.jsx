// src/components/ChatWidget/index.jsx
import { useState, useRef, useEffect } from 'react';
import { useChat } from '../../hooks/useChat';
import { ChatHeader } from './ChatHeader';
// import ChatMessage  from './ChatMessage';
import { ChatInput } from './ChatInput';
import ChatBody from './ChatBody';

export default function ChatWidget() {
    const [isOpen, setIsOpen] = useState(false);
    const { messages, isLoading, error, handleSendMessage } = useChat();
    const messagesEndRef = useRef(null);

    // Auto scroll
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, isLoading]);

    return (
        <div className="fixed bottom-5 right-5 z-50 flex flex-col items-end font-sans text-sm">
            {/* CHAT WINDOW */}
            {isOpen && (
                <div className="bg-white w-[350px] sm:w-[380px] h-[550px] rounded-2xl shadow-2xl border border-gray-100 flex flex-col mb-4 overflow-hidden transition-all duration-300">
                    <ChatHeader onClose={() => setIsOpen(false)} />

                    {/* Message List */}
                    <div className="flex-1 overflow-y-auto p-4 bg-gray-50 scrollbar-hide flex flex-col gap-4">
                        <div className="text-center text-xs text-gray-400 font-medium tracking-widest my-2">TODAY</div>
                        
                        {/* {messages.map((msg) => (
                            <ChatMessage key={msg.id} msg={msg} />
                        ))} */}
                        <ChatBody messages={messages} />

                        {/* Loading Indicator */}
                        {isLoading && (
                            <div className="flex gap-2 justify-start">
                                <div className="bg-white border border-gray-100 p-3 rounded-2xl rounded-tl-sm shadow-sm flex gap-1 items-center">
                                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0.2s'}}></div>
                                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0.4s'}}></div>
                                </div>
                            </div>
                        )}

                        {/* Error */}
                        {error && <div className="text-center text-xs text-red-500 my-2">{error}</div>}
                        
                        <div ref={messagesEndRef} />
                    </div>

                    <ChatInput onSendMessage={handleSendMessage} isLoading={isLoading} />
                </div>
            )}

            {/* TRIGGER BUTTON */}
            <button 
                onClick={() => setIsOpen(!isOpen)}
                className={`${isOpen ? 'bg-gray-800' : 'bg-blue-600 hover:bg-blue-700'} text-white w-14 h-14 rounded-full shadow-xl flex items-center justify-center transition-all duration-300 transform hover:scale-105`}
            >
                {isOpen ? (
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                ) : (
                    <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"></path></svg>
                )}
            </button>
        </div>
    );
}