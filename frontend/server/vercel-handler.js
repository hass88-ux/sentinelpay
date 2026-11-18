import {accountConfig} from './account-config.js';
import {createHandler} from './incident-ai.js';
import {createPrivateAiHandler} from './private-ai.js';

export function createVercelHandler(snapshot,{fetcher=fetch}={}) {
  const demo=createHandler(snapshot,{fetcher}),privateAi=createPrivateAiHandler({fetcher});
  return async(request,env)=>{
    const url=new URL(request.url);
    const json=(value,status=200)=>Response.json(value,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
    if(url.pathname==='/api/account-config') {
      const config=accountConfig(env);
      return json(config.enabled?{...config,apiBase:url.origin}:config);
    }
    if(/^\/api\/uploads\/[a-f0-9-]{36}\/questions$/.test(url.pathname))return privateAi(request,env);
    // Same-origin gateway, restricted to the existing upload API. Java still verifies
    // every bearer token and enforces ownership, RLS, size limits and request quotas.
    if(/^\/api\/uploads(?:\/[a-f0-9-]{36})?$/.test(url.pathname)) {
      if(!['GET','POST','DELETE'].includes(request.method))return json({detail:'Method not allowed.'},405);
      if(request.method!=='GET'&&request.headers.get('Origin')!==url.origin)return json({detail:'Use the upload form on this site.'},403);
      const authorization=request.headers.get('Authorization');
      if(!authorization?.startsWith('Bearer ')||authorization.length>8192)return json({detail:'Sign in to continue.'},401);
      try {
        const upstream=new URL(env.UPLOADS_API_URL);
        if(upstream.protocol!=='https:')throw new Error('Invalid upstream');
        const headers={Authorization:authorization};
        if(request.headers.has('Content-Type'))headers['Content-Type']=request.headers.get('Content-Type');
        const response=await fetcher(upstream.origin+url.pathname,{method:request.method,headers,
          body:['GET','HEAD'].includes(request.method)?undefined:await request.arrayBuffer(),
          redirect:'manual',signal:AbortSignal.timeout(55000)});
        if(response.status>=300&&response.status<400)return json({detail:'The upload service returned an unexpected redirect.'},502);
        return new Response(response.body,{status:response.status,headers:{'Content-Type':response.headers.get('Content-Type')??'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
      }catch{return json({detail:'The upload service is unavailable or timed out. Refresh saved files before retrying an upload.'},503)}
    }
    return demo(request,env);
  };
}
