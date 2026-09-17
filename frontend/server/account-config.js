export function accountConfig(env) {
  const supabaseUrl=env.SUPABASE_URL, publishableKey=env.SUPABASE_PUBLISHABLE_KEY, apiBase=env.UPLOADS_API_URL;
  if(!supabaseUrl||!publishableKey||!apiBase)return {enabled:false};
  try {
    const auth=new URL(supabaseUrl),api=new URL(apiBase);
    if(auth.protocol!=='https:'||api.protocol!=='https:'||!auth.hostname.endsWith('.supabase.co')||!publishableKey.startsWith('sb_publishable_'))return {enabled:false};
    return {enabled:true,supabaseUrl:auth.origin,publishableKey,apiBase:api.origin,emailAuthReady:env.EMAIL_AUTH_READY==='true'};
  }catch{return {enabled:false}}
}
