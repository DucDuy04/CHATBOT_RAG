import axiosInstance from "./axiosInstance";

export const createWidget = async ({ name, allowedOrigin, uiConfig = {} }) => {
  const response = await axiosInstance.post("/api/widgets", {
    name,
    allowedOrigin,
    uiConfig,
  });
  return response.data;
};
