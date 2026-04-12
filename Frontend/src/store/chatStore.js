export const createInitialChatState = () => ({
  conversations: [],
  messages: [],
  selectedConversationId: null,
  isTyping: false,
});

const chatStore = createInitialChatState();

export default chatStore;
