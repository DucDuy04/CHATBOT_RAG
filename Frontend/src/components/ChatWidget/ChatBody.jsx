// import { useChat } from '../../hooks/useChat';
import ChatMessage from './ChatMessage';

export default function ChatBody( {messages}) {
  // const { messages } = useChat();

  return (
    <div className="chat-body">
      {messages.length === 0 ? (
        <div className="empty-state">
          <p>Xin chào! 👋 Hãy bắt đầu cuộc trò chuyện.</p>
        </div>
      ) : (
        <div className="messages-list">
          {messages.map((msg) => (
            <ChatMessage
              key={msg.id}
              message={msg.content}
              sender={msg.sender}
              timestamp={msg.timestamp}
            />
          ))}
        </div>
      )}
    </div>
  );
}
