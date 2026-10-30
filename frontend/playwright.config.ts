import { defineConfig } from '@playwright/test';

// Las pruebas E2E se escriben aquí y se ejecutan en el CI contra la aplicación desplegada (BASE_URL).
export default defineConfig({
  testDir: './e2e',
  use: { baseURL: process.env['BASE_URL'] ?? 'http://localhost:4200' },
});
