import { defineConfig } from 'vite';
export default defineConfig({
  root: 'frontend',
  build: { outDir: '../dist/client', emptyOutDir: true },
  server: { proxy: { '/api/uploads': 'http://127.0.0.1:8083', '/api/account-config': 'http://127.0.0.1:8083', '/api': 'http://127.0.0.1:8082', '/actuator': 'http://127.0.0.1:8082' } },
  test: { environment: 'node', include: ['src/**/*.test.js', 'server/**/*.test.js'] },
});
