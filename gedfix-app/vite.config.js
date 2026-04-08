import { sveltekit } from "@sveltejs/kit/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

// @ts-expect-error process is a nodejs global
const host = process.env.TAURI_DEV_HOST;
// @ts-expect-error process is a nodejs global
const isWeb = process.env.VITE_WEB === 'true';
const LARGE_ROUTE_PAGES = new Set([
  // Keep route-level pages in dedicated chunks.
  "src/routes/cleanup/+page.svelte",
  "src/routes/services/+page.svelte",
  "src/routes/people/[xref]/+page.svelte",
]);

/** @param {string} id */
function packageChunkName(id) {
  const parts = id.split("node_modules/");
  if (parts.length < 2) return null;
  const pkgPath = parts[1];

  // Handle pnpm layout: .pnpm/pkg@version/node_modules/@scope/name/...
  if (pkgPath.startsWith(".pnpm/")) {
    const nested = pkgPath.split("/node_modules/")[1];
    if (!nested) return "vendor";
    const nestedSegments = nested.split("/");
    const nestedName = nestedSegments[0].startsWith("@")
      ? `${nestedSegments[0]}/${nestedSegments[1] ?? "unknown"}`
      : nestedSegments[0];
    return `vendor-${nestedName.replace(/[\/@]/g, "_")}`;
  }

  const segments = pkgPath.split("/");
  const packageName = segments[0].startsWith("@")
    ? `${segments[0]}/${segments[1] ?? "unknown"}`
    : segments[0];
  return `vendor-${packageName.replace(/[\/@]/g, "_")}`;
}

/** @param {string} id */
function manualChunks(id) {
  const normalizedId = id.replaceAll("\\", "/");

  if (normalizedId.includes("/node_modules/sql.js/") || normalizedId.includes("sqlite")) {
    return "sqlite";
  }

  if (/\/src\/lib\/i18n\/(en|es|de|fr|pt)\.ts$/.test(normalizedId)) {
    return "i18n";
  }

  if (normalizedId.includes("/@vladmandic/face-api/")) {
    return "face-ai";
  }

  const candidateRoutePath = normalizedId.split("/src/routes/")[1];
  if (candidateRoutePath && LARGE_ROUTE_PAGES.has(`src/routes/${candidateRoutePath}`)) {
    const routePath = normalizedId
      .split("/src/routes/")[1]
      ?.replace("/+page.svelte", "")
      .replace(/[\/[\]]+/g, "-")
      .replace(/^-+|-+$/g, "")
      || "page";
    return `route-${routePath}`;
  }

  const pkgChunk = packageChunkName(normalizedId);
  if (pkgChunk) return pkgChunk;

  return undefined;
}

export default defineConfig({
  plugins: [tailwindcss(), sveltekit()],
  clearScreen: false,
  build: {
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      output: {
        manualChunks,
      },
    },
  },
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
