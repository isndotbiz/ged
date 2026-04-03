import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.js';

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'node',
      include: ['src/lib/__tests__/**/*.test.ts'],
      clearMocks: true,
      restoreMocks: true,
      mockReset: true,
    },
  })
);
