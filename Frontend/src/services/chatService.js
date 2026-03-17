// src/services/chatService.js
export const chatService = {
    sendMessage: async (userMessageText) => {
        try {
            // ==========================================
            // SAU NÀY BẠN MỞ COMMENT ĐOẠN NÀY ĐỂ GỌI API THẬT
            // ==========================================

            // const response = await fetch('/api/chat', {
           /* 
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: userMessageText, history: currentHistory })
            });
            if (!response.ok) throw new Error('API Error');
            const data = await response.json();
            return data.reply; 
            */

            // MÔ PHỎNG API CALL (Giống hệt file gốc của bạn)
            await new Promise(resolve => setTimeout(resolve, 1500));
            
            let botReply = "Tôi đã nhận được tin nhắn của bạn. Đang trong chế độ demo, chưa kết nối backend thực.";
            if(userMessageText.toLowerCase().includes("pro plan")) {
                botReply = "Great choice! Our Pro plan includes:\n• 50,000 API requests/month\n• Priority email support\n• Custom data training";
            }
            
            return botReply;

        } catch (error) {
            console.error("Chat API Error:", error);
            throw error; // Ném lỗi ra để Hook bắt lại hiển thị lên UI
        }
    }
};