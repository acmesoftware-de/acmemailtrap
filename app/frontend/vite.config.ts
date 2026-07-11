import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The built bundle is served by Spring Boot from the jar's static/. During `npm run
// dev`, proxy the API to the backend so same-origin fetches work.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8090',
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
})
