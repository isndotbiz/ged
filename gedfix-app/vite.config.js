import { sveltekit } from "@sveltejs/kit/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

// @ts-expect-error process is a nodejs global
const host = process.env.TAURI_DEV_HOST;
// @ts-expect-error process is a nodejs global
const isWeb = process.env.VITE_WEB === 'true';
const filePath = fileURLToPath(import.meta.url);
const rootDir = path.dirname(filePath);
const ROUTE_CHUNK_THRESHOLD = 100 * 1024;

function collectLargeRoutePages() {
  const routesDir = path.join(rootDir, "src", "routes");
  const largePages = new Set();

  /** @param {string} dir */
  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const entryPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(entryPath);
        continue;
      }
      if (entry.isFile() && entry.name === "+page.svelte") {
        const stat = fs.statSync(entryPath);
        if (stat.size > ROUTE_CHUNK_THRESHOLD) {
          largePages.add(path.normalize(entryPath));
        }
      }
    }
  }

  walk(routesDir);
  return largePages;
}

const largeRoutePages = collectLargeRoutePages();

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
  const normalizedId = path.normalize(id);

  if (normalizedId.includes(`node_modules${path.sep}sql.js${path.sep}`) || normalizedId.includes("sqlite")) {
    return "sqlite";
  }

  if (/src[\/\\]lib[\/\\]i18n[\/\\](en|es|de|fr|pt)\.ts$/.test(normalizedId)) {
    return "i18n";
  }

  if (largeRoutePages.has(normalizedId)) {
    const routePath = normalizedId
      .split(`${path.sep}src${path.sep}routes${path.sep}`)[1]
      ?.replace(`${path.sep}+page.svelte`, "")
      .replace(/[\/\\[\]]+/g, "-")
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
