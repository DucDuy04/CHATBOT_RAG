// src/App.jsx
import ChatWidget from './components/ChatWidget/index.jsx';

function App() {
  return (
    <div className="bg-gray-100 h-screen w-screen flex flex-col items-center justify-center">
      <h1 className="text-3xl font-bold text-gray-700">Test Widget RAG Chatbot</h1>
      <p className="mt-4 text-gray-500">Bấm vào biểu tượng tin nhắn góc dưới cùng bên phải để chat.</p>
      
      {/* Gọi Widget */}
      <ChatWidget />
    </div>
  );
}

export default App;