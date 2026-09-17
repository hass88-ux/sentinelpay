import {ApiError,answerFacts,readJsonLimited} from './incident-ai.js';

// The browser supplies only a file ID and currency. Evidence is fetched from the
// owner-authorized Java endpoint, never accepted from client-provided JSON.
export function createPrivateAiHandler({fetcher=fetch}={}) {
  return async (request,env)=>{
    const headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'};
    const json=(body,status=200)=>Response.json(body,{status,headers});
    try {
      const url=new URL(request.url);
      const match=url.pathname.match(/^\/api\/uploads\/([a-f0-9-]{36})\/questions$/);
      if(!match)return json({detail:'Endpoint not found.'},404);
      if(request.method!=='POST')return json({detail:'Use POST.'},405);
      if(request.headers.get('Origin')!==url.origin)throw new ApiError(403,'Use the question form on this site.');
      if(!request.headers.get('Content-Type')?.startsWith('application/json'))throw new ApiError(415,'Send JSON.');
      const token=request.headers.get('Authorization');
      if(!token?.startsWith('Bearer ')||token.length>8192)throw new ApiError(401,'Sign in to continue.');
      const body=await readJsonLimited(request.body,4096);
      if(!body||Array.isArray(body)||Object.keys(body).some(k=>!['question','currency','consent'].includes(k))||body.consent!==true)
        throw new ApiError(400,'Consent to share aggregate metrics with Groq is required.');
      if(typeof body.question!=='string'||!body.question.trim()||body.question.length>500||!/^([A-Z]{3})$/.test(body.currency??''))
        throw new ApiError(400,'Choose a currency and ask a question of 1–500 characters.');
      if(!env.GROQ_API_KEY)throw new ApiError(503,'AI is unavailable. Your metrics and checks still work.');
      const upstream=new URL(env.UPLOADS_API_URL);
      if(upstream.protocol!=='https:')throw new ApiError(503,'Upload API is unavailable.');
      const response=await fetcher(`${upstream.origin}/api/uploads/${match[1]}/ai-context`,{
        method:'POST',headers:{Authorization:token,'Content-Type':'application/json'},
        body:JSON.stringify({currency:body.currency}),signal:AbortSignal.timeout(55000),redirect:'error'});
      if(!response.ok){await response.body?.cancel();throw new ApiError([400,401,404,429].includes(response.status)?response.status:503,
        response.status===401?'Your session expired. Sign in again.':response.status===404?'This file is unavailable or belongs to another account.':
        response.status===429?'AI request limit reached. Try later (3/minute, 20/day per account; shared capacity also applies).':
        response.status===400?'AI needs a valid currency and at most 120 observed minutes in that currency.':'The upload service is unavailable. Try again shortly.');}
      const raw=await readJsonLimited(response.body,180000);
      // Explicitly select fields even if a later Java response adds file metadata.
      const facts={source:'Private uploaded payment metrics',currency:raw.currency,
        observations:raw.observations.map(({bucket,totalCount,failedCount,averageLatencyMs})=>({bucket,totalCount,failedCount,averageLatencyMs})),
        findings:raw.findings.map(({bucket,currency,evaluations})=>({bucket,currency,evaluations:evaluations.map(({rule,state,observed,warningThreshold,criticalThreshold,explanation})=>({rule,state,observed,warningThreshold,criticalThreshold,explanation}))}))};
      return json(await answerFacts({facts,question:body.question,key:env.GROQ_API_KEY,fetcher}));
    } catch(e){return json({detail:e instanceof ApiError?e.message:'AI could not complete this request. Your saved file is unchanged.'},e instanceof ApiError?e.status:503);}
  };
}
