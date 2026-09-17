import {describe,it,expect} from 'vitest';
import {accountConfig} from './account-config';
describe('public account configuration',()=>{
  it('stays disabled until the API is deployed',()=>expect(accountConfig({})).toEqual({enabled:false}));
  it('never exposes server secrets',()=>{
    const c=accountConfig({SUPABASE_URL:'https://test.supabase.co',SUPABASE_PUBLISHABLE_KEY:'sb_publishable_example',UPLOADS_API_URL:'https://test.onrender.com',GROQ_API_KEY:'secret',DATABASE_PASSWORD:'secret'});
    expect(c).toEqual({enabled:true,supabaseUrl:'https://test.supabase.co',publishableKey:'sb_publishable_example',apiBase:'https://test.onrender.com',emailAuthReady:false,googleAuthReady:false});
  });
  it('rejects insecure origins and secret API keys',()=>{
    expect(accountConfig({SUPABASE_URL:'https://test.supabase.co',SUPABASE_PUBLISHABLE_KEY:'sb_secret_example',UPLOADS_API_URL:'https://test.onrender.com'}).enabled).toBe(false);
    expect(accountConfig({SUPABASE_URL:'https://test.supabase.co',SUPABASE_PUBLISHABLE_KEY:'sb_publishable_example',UPLOADS_API_URL:'http://test.onrender.com'}).enabled).toBe(false);
  });
});
