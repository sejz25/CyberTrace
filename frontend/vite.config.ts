import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Keep browser API requests on port 5173 while forwarding them to Spring Boot.
// Without this, Vite serves index.html for /api requests and JSON parsing fails.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
});
