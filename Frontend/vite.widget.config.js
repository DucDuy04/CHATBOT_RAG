import { defineConfig } from "vite";

export default defineConfig({
  build: {
    lib: {
      entry:    "widget/widget.js",
      name:     "RagChatbot",
      fileName: "chatbot-widget",
      formats:  ["iife"],          // 1 file JS duy nhất, chạy ngay không cần import
    },
    outDir:          "dist-widget",
    emptyOutDir:     true,
    rollupOptions: {
      output: {
        assetFileNames: "widget.[ext]", // CSS output thành widget.css
      },
    },
  },
});