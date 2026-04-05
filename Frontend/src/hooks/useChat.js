// src/hooks/useChat.js
import { useState } from 'react';
import { chatService } from '../services/chatService';
import { getCurrentTime } from '../utils/timeUtils';

export const useChat = () => {
    const [messages, setMessages] = useState([
        { id: 1, sender: "bot", text: "Hello! I'm your AI assistant. How can I help you with our services today?", time: getCurrentTime() }
    ]);
    const[isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    const handleSendMessage = async (text) => {
        const userMsg = { id: Date.now(), sender: "user", text, time: getCurrentTime() };
        
        setMessages(prev =>[...prev, userMsg]);
        setIsLoading(true);
        setError(null);

        try {
            const replyText = await chatService.sendMessage(text, messages);
            const botMsg = { id: Date.now() + 1, sender: "bot", text: replyText, time: getCurrentTime() };
            setMessages(prev => [...prev, botMsg]);
        } catch (err) {
            setError("Oops! Đã xảy ra lỗi kết nối. Vui lòng thử lại.", err);
        } finally {
            setIsLoading(false);
        }
    };

    return { messages, isLoading, error, handleSendMessage };
};