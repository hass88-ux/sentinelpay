import {afterEach,expect,it,vi} from 'vitest';
import {uploadRequest} from './uploads';
afterEach(()=>vi.unstubAllGlobals());
it('sends a bearer token and leaves multipart content type to the browser',async()=>{
  const fetch=vi.fn().mockResolvedValue(new Response('{"id":"file"}',{status:201}));vi.stubGlobal('fetch',fetch);
  const body=new FormData();await uploadRequest('https://api.example','token','',{method:'POST',body});
  expect(fetch.mock.calls[0][1].headers).toEqual({Authorization:'Bearer token'});
  expect(fetch.mock.calls[0][1].cache).toBe('no-store');
});
it('does not treat an expired login as an empty file list',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('',{status:401})));
  await expect(uploadRequest('https://api.example','expired')).rejects.toThrow('session expired');
});
it('accepts an empty delete response',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(null,{status:204})));
  expect(await uploadRequest('https://api.example','token','/id',{method:'DELETE'})).toBeNull();
});
