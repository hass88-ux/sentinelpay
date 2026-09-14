import assert from 'node:assert/strict';
import worker from '../dist/server/index.js';

const page = await worker.fetch(new Request('https://example.com/'), {});
assert.equal(page.status, 200);
const html = await page.text();
assert.ok(html.includes('id="root"'));
for (const [, asset] of html.matchAll(/(?:src|href)="([^" ]+\.(?:js|css|svg))"/g)) {
  const response = await worker.fetch(new Request(new URL(asset, 'https://example.com')), {});
  assert.equal(response.status, 200, asset);
  assert.ok((await response.text()).length > 0, asset);
}
const data = await worker.fetch(new Request('https://example.com/demo/snapshot.json'), {});
assert.equal((await data.json()).cases.length, 1);
const status = await worker.fetch(new Request('https://example.com/api/demo/ai/status'), {});
assert.equal((await status.json()).enabled, false);
const missing = await worker.fetch(new Request('https://example.com/missing'), {});
assert.equal(missing.status, 404);
console.log('Built Worker serves HTML, assets, demo data, AI status, and missing routes correctly.');
