import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const backendTarget = process.env.VITE_BACKEND_TARGET ?? 'http://127.0.0.1:8080'
const developmentProxy = () => ({
  target: backendTarget,
  changeOrigin: true,
  configure(proxy: { on: (event: string, handler: (request: { removeHeader: (name: string) => void }) => void) => void }) {
    proxy.on('proxyReq', (request) => request.removeHeader('origin'))
  },
})

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    setupFiles: ['./src/test/setup.ts'],
    coverage: { reporter: ['text', 'html', 'lcov'], include: ['src/api.ts', 'src/tryon-selection.ts'] },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': developmentProxy(),
      '/assets': developmentProxy(),
      '/actuator': developmentProxy(),
    },
  },
})
