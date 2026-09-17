import {describe,it,expect,vi} from 'vitest';
import {createPrivateAiHandler} from './private-ai';
const id='11111111-1111-4111-8111-111111111111';
const env={UPLOADS_API_URL:'https://java.example',GROQ_API_KEY:'provider-key'};
const req=(body={question:'What changed?',currency:'USD',consent:true},token='Bearer test')=>new Request(`https://site.example/api/uploads/${id}/questions`,{method:'POST',headers:{Origin:'https://site.example','Content-Type':'application/json',Authorization:token},body:JSON.stringify(body)});
describe('private upload AI boundary',()=>{
  it('requires explicit consent before any external request',async()=>{
    const fetcher=vi.fn();const r=await createPrivateAiHandler({fetcher})(req({question:'Explain',currency:'USD',consent:false}),env);
    expect(r.status).toBe(400);expect(fetcher).not.toHaveBeenCalled();
  });
  it.each([401,404,429])('does not call Groq when Java returns %i',async status=>{
    const fetcher=vi.fn().mockResolvedValue(new Response('',{status}));
    const r=await createPrivateAiHandler({fetcher})(req(),env);expect(r.status).toBe(status);expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('uses server-owned facts and never forwards the user token or file metadata to Groq',async()=>{
    const fetcher=vi.fn().mockResolvedValueOnce(Response.json({currency:'USD',filename:'secret.csv',owner:'secret-owner',
      observations:[{bucket:'2026-01-01T12:00:00Z',totalCount:25,failedCount:10,averageLatencyMs:1500,totalAmount:9999}],
      findings:[{bucket:'2026-01-01T12:00:00Z',currency:'USD',evaluations:[{rule:'FAILURE_RATE',state:'WARNING',observed:40,warningThreshold:20,criticalThreshold:50,explanation:'Failure rate.'}]}]}))
      .mockResolvedValueOnce(Response.json({choices:[{finish_reason:'stop',message:{content:JSON.stringify({answer:'Failure rate was elevated.',evidenceRules:['FAILURE_RATE'],nextChecks:[]})}}]}));
    const r=await createPrivateAiHandler({fetcher})(req(),env);expect(r.status).toBe(200);
    expect(fetcher.mock.calls[0][1].headers.Authorization).toBe('Bearer test');
    const sent=JSON.stringify(fetcher.mock.calls[1]);
    for(const secret of ['secret.csv','secret-owner','totalAmount','Bearer test'])expect(sent).not.toContain(secret);
    expect((await r.json()).source).toBe('PRIVATE_UPLOAD_AGGREGATES');
  });
  it('rejects supplied evidence',async()=>{
    const fetcher=vi.fn();expect((await createPrivateAiHandler({fetcher})(req({question:'Explain',currency:'USD',consent:true,facts:{}}),env)).status).toBe(400);
    expect(fetcher).not.toHaveBeenCalled();
  });
});

