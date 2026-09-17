import snapshot from '../public/demo/snapshot.json';
import assets from '../../.sites-runtime/static-assets.js';
import { createHandler } from './incident-ai.js';
import { accountConfig } from './account-config.js';
import { createPrivateAiHandler } from './private-ai.js';

const api = createHandler(snapshot);
const privateAi=createPrivateAiHandler();
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const accounts=accountConfig(env);
    if(url.pathname==='/api/account-config')return Response.json(accounts,{headers:{'Cache-Control':'no-store'}});
    if(url.pathname.startsWith('/api/uploads/'))return privateAi(request,env);
    if (url.pathname.startsWith('/api/')) return api(request, env);
    if (!['GET', 'HEAD'].includes(request.method)) return new Response('Method not allowed', { status: 405 });
    const path = url.pathname === '/' ? '/index.html' : url.pathname;
    const asset = Object.hasOwn(assets, path) ? assets[path] : null;
    if (!asset) return new Response('Not found', { status: 404 });
    return new Response(request.method === 'HEAD' ? null : asset.body, { headers: {
      'Content-Type': asset.type, 'X-Content-Type-Options': 'nosniff',
      'Cache-Control': path.startsWith('/assets/') ? 'public, max-age=31536000, immutable' : 'no-cache',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
      'Content-Security-Policy': `default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' ${accounts.enabled?accounts.supabaseUrl+' '+accounts.apiBase:''}; object-src 'none'; base-uri 'self'; frame-ancestors 'self' https://chatgpt.com https://*.chatgpt.com`,
    } });
  },
};
