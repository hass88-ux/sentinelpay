import { defineConfig } from 'vite';
export default defineConfig({
  root: 'frontend',
  build: { outDir: '../dist', emptyOutDir: true },
  server: { proxy: { '/api': 'http://127.0.0.1:8082', '/actuator': 'http://127.0.0.1:8082' } },
  test: { environment: 'node', include: ['src/**/*.test.js'] },
});
