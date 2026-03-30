import { sveltekit } from "@sveltejs/kit/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

// @ts-expect-error process is a nodejs global
const host = process.env.TAURI_DEV_HOST;
// @ts-expect-error process is a nodejs global
const isWeb = process.env.VITE_WEB === 'true';

export default defineConfig({
  plugins: [tailwindcss(), sveltekit()],
  clearScreen: false,
  optimizeDeps: {
    exclude: ['sql.js'],
  },
  server: {
    port: isWeb ? 5173 : 1420,
    strictPort: !isWeb,
    host: host || false,
    hmr: host
      ? { protocol: "ws", host, port: 1421 }
      : undefined,
    watch: {
      ignored: ["**/src-tauri/**"],
    },
    headers: isWeb ? {
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Embedder-Policy': 'require-corp',
    } : undefined,
  },
});
