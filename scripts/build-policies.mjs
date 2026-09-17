import {readFile, writeFile} from 'node:fs/promises';

// These approved pages are static and accessible without signing in.
export async function buildPolicies() {
  const source=await readFile('docs/public-policies.md','utf8');
  const escape=text=>text.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
  for (const [slug,title] of [['privacy','Privacy policy'],['terms','Terms of use']]) {
    const section=source.split(`## ${title}`)[1]?.split('\n## ')[0]?.trim();
    if(!section)throw new Error(`Missing approved ${title}`);
    const content=section.split(/\r?\n\s*\r?\n/).map(block=>block.startsWith('### ')?`<h2>${escape(block.slice(4))}</h2>`:`<p>${escape(block)}</p>`).join('\n');
    await writeFile(`dist/client/${slug}.html`,`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${title} · SentinelPay</title><link rel="stylesheet" href="/policy.css"><link rel="icon" href="/favicon.svg"></head><body><main><a href="/">← SentinelPay</a><h1>${title}</h1>${content}<nav aria-label="Policy navigation"><a href="/privacy.html">Privacy policy</a><a href="/terms.html">Terms of use</a><a href="/?account=1">My uploads</a></nav></main></body></html>`);
  }
}
