import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

/**
 * Vite 配置。
 * - dev 端口 5173；/api 代理到后端 8080，前端请求统一写 /api/xxx 相对路径；
 * - @ 指向 src，import 一律用 '@/...'，避免 ../.. 相对路径。
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
