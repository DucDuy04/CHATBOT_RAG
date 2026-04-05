export default function ToggleBot({ isOpen, onClick }) {
  return (
    <button
      className={`toggle-bot ${isOpen ? 'open' : 'closed'}`}
      onClick={onClick}
      title={isOpen ? 'Đóng chatbot' : 'Mở chatbot'}
    >
      <span className="chat-icon">💬</span>
    </button>
  );
}
