import { build } from 'vite';
import { readdir, readFile, mkdir, writeFile, copyFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Preserve the existing React/Vite app; package a small server for secret-bearing AI calls.
const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
if (path.resolve('.') !== project) throw new Error('Run the build from the repository root.');
// Only remove this project's verified generated output, including older static builds.
const output = path.join(project, 'dist');
await rm(output, { recursive: true, force: true });
await build({ build: { outDir: '../dist/client', emptyOutDir: true } });
const assets = {};
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml' };
async function collect(directory, prefix = '') {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const filename = path.join(directory, entry.name), url = `${prefix}/${entry.name}`;
    if (entry.isDirectory()) await collect(filename, url);
    else {
      const type = types[path.extname(entry.name)];
      if (!type) throw new Error(`Add explicit binary asset handling for ${url}`);
      assets[url] = { type, body: await readFile(filename, 'utf8') };
    }
  }
}
await collect('dist/client');
await mkdir('.sites-runtime', { recursive: true });
await writeFile('.sites-runtime/static-assets.js', `export default ${JSON.stringify(assets)};\n`);
await build({ configFile: false, build: { ssr: 'frontend/server/worker.js', outDir: 'dist/server',
  emptyOutDir: true, rollupOptions: { output: { entryFileNames: 'index.js' } } } });
await mkdir('dist/.openai', { recursive: true });
await copyFile('.openai/hosting.json', 'dist/.openai/hosting.json');
