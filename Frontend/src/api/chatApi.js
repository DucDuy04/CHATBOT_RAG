import axiosInstance from "./axiosInstance";

// Gửi câu hỏi lên BE, nhận câu trả lời
// request: { sessionId, message }
// response: { answer, sources: [{ fileName, chunkText }] }
export const sendMessage = async (sessionId, message) => {
  const response = await axiosInstance.post("/api/chat", {
    sessionId,
    message,
  });
  return response.data;
};