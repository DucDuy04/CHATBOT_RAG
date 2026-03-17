import EmptyState from '../ui/EmptyState.jsx';
import MessageBubble from './MessageBubble.jsx';

function ChatWindow({ messages = [] }) {
  if (messages.length === 0) {
    return <EmptyState title="No messages" description="Start a conversation to see messages here." />;
  }

  return (
    <section>
      {messages.map((message) => (
        <MessageBubble
          key={message.id ?? `${message.role}-${message.content}`}
          role={message.role}
          content={message.content}
        />
      ))}
    </section>
  );
}

export default ChatWindow;
