import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import { readFileSync, existsSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'serve-dist-widget',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.startsWith('/dist-widget/')) {
            // Prefer built bundle over stale copies under public/dist-widget
            const builtPath = resolve(__dirname, req.url.slice(1))
            const publicPath = resolve(__dirname, 'public', req.url.slice(1))
            const filePath = existsSync(builtPath) ? builtPath : publicPath
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
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})