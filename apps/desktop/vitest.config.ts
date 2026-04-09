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
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov', 'html'],
        include: ['src/lib/**/*.ts'],
        exclude: ['src/lib/__tests__/**', 'src/lib/**/*.d.ts'],
        thresholds: {
          lines: 60,
          functions: 60,
          branches: 50,
        },
      },
    },
  })
);
