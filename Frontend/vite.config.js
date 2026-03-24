import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import { readFileSync, existsSync } from 'fs'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Serve dist-widget folder as static files
    {
      name: 'serve-dist-widget',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.startsWith('/dist-widget/')) {
            const filePath = resolve(__dirname, req.url.slice(1))
            if (existsSync(filePath)) {
              const ext = filePath.split('.').pop()
              const contentType = ext === 'css' ? 'text/css'
                                : ext === 'js' ? 'application/javascript'
                                : 'application/octet-stream'
              res.setHeader('Content-Type', contentType)
              res.setHeader('Access-Control-Allow-Origin', '*')
              res.end(readFileSync(filePath))
              return
            }
          }
          next()
        })
      }
    }
  ],
})
