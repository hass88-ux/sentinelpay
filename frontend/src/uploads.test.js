import {afterEach,expect,it,vi} from 'vitest';
import {uploadRequest} from './uploads';
afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers();});
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
it('never retries an upload after an ambiguous network failure',async()=>{
  const fetch=vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));vi.stubGlobal('fetch',fetch);
  await expect(uploadRequest('https://api.example','token','',{method:'POST',body:new FormData()})).rejects.toThrow('may already have been saved');
  expect(fetch).toHaveBeenCalledTimes(1);
});
it('does not expose server error pages or retry a failed mutation',async()=>{
  const fetch=vi.fn().mockResolvedValue(new Response('private stack trace',{status:503}));vi.stubGlobal('fetch',fetch);
  await expect(uploadRequest('https://api.example','token','/id',{method:'DELETE'})).rejects.toThrow('deletion may already have completed');
  expect(fetch).toHaveBeenCalledTimes(1);
});
it('preserves caller cancellation',async()=>{
  const controller=new AbortController();controller.abort(new DOMException('Cancelled','AbortError'));
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(controller.signal.reason));
  await expect(uploadRequest('https://api.example','token','',{signal:controller.signal})).rejects.toMatchObject({name:'AbortError'});
});
it('times out stalled requests and clears its timer',async()=>{
  vi.useFakeTimers();
  vi.stubGlobal('fetch',vi.fn((_url,{signal})=>new Promise((_resolve,reject)=>signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError'))))));
  const result=expect(uploadRequest('https://api.example','token','',{method:'POST'})).rejects.toThrow('may already have been saved');
  await vi.advanceTimersByTimeAsync(90000);await result;
  expect(vi.getTimerCount()).toBe(0);
});
it('explains rate limits without retrying',async()=>{
  const fetch=vi.fn().mockResolvedValue(new Response('',{status:429}));vi.stubGlobal('fetch',fetch);
  await expect(uploadRequest('https://api.example','token')).rejects.toThrow('Request limit reached');
  expect(fetch).toHaveBeenCalledTimes(1);
});
