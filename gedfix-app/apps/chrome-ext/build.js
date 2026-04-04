import { build, context } from 'esbuild';
import { cpSync, mkdirSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const dist = resolve(__dirname, 'dist');
const watch = process.argv.includes('--watch');

// Ensure dist directories exist
mkdirSync(resolve(dist, 'sidepanel'), { recursive: true });
mkdirSync(resolve(dist, 'content'), { recursive: true });
mkdirSync(resolve(dist, 'popup'), { recursive: true });
mkdirSync(resolve(dist, 'icons'), { recursive: true });

// Copy static files
cpSync(resolve(__dirname, 'manifest.json'), resolve(dist, 'manifest.json'));
cpSync(resolve(__dirname, 'background.js'), resolve(dist, 'background.js'));
cpSync(resolve(__dirname, 'sidepanel/index.html'), resolve(dist, 'sidepanel/index.html'));
cpSync(resolve(__dirname, 'sidepanel/styles.css'), resolve(dist, 'sidepanel/styles.css'));
cpSync(resolve(__dirname, 'popup/popup.html'), resolve(dist, 'popup/popup.html'));

// Copy icons (use favicon as placeholder if icons dir doesn't exist)
const iconsDir = resolve(__dirname, 'icons');
if (existsSync(iconsDir)) {
  cpSync(iconsDir, resolve(dist, 'icons'), { recursive: true });
} else {
  // Create placeholder icons from favicon if available
  const favicon = resolve(__dirname, '../../static/favicon.png');
  if (existsSync(favicon)) {
    for (const size of ['icon-16.png', 'icon-48.png', 'icon-128.png']) {
      cpSync(favicon, resolve(dist, 'icons', size));
    }
  }
}

// Fix sidepanel/index.html to reference .js instead of .ts
const indexHtml = resolve(dist, 'sidepanel/index.html');
import { readFileSync, writeFileSync } from 'fs';
const html = readFileSync(indexHtml, 'utf-8');
writeFileSync(indexHtml, html.replace('./app.ts', './app.js'));

const sharedConfig = {
  bundle: true,
  minify: !watch,
  sourcemap: watch ? 'inline' : false,
  target: 'chrome120',
  format: 'esm',
  logLevel: 'info',
};

// Bundle sidepanel app (TypeScript, imports sql.js)
const sidepanelEntry = {
  ...sharedConfig,
  entryPoints: [resolve(__dirname, 'sidepanel/app.ts')],
  outfile: resolve(dist, 'sidepanel/app.js'),
  external: [], // sql.js will be bundled
};

// Bundle content scripts (JS, imports extractor)
const contentEntry = {
  ...sharedConfig,
  entryPoints: [resolve(__dirname, 'content/detector.js')],
  outfile: resolve(dist, 'content/detector.js'),
  format: 'iife', // Content scripts must be IIFE, not ESM
};

// Bundle popup (JS, imports sql.js)
const popupEntry = {
  ...sharedConfig,
  entryPoints: [resolve(__dirname, 'popup/popup.js')],
  outfile: resolve(dist, 'popup/popup.js'),
};

if (watch) {
  const contexts = await Promise.all([
    context(sidepanelEntry),
    context(contentEntry),
    context(popupEntry),
  ]);
  await Promise.all(contexts.map(ctx => ctx.watch()));
  console.log('Watching for changes...');
} else {
  await Promise.all([
    build(sidepanelEntry),
    build(contentEntry),
    build(popupEntry),
  ]);
  console.log('Build complete → dist/');
}
