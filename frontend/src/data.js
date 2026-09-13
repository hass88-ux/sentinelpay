export const starterQuestions = ['Why was this flagged?', 'What changed before the spike?', 'What should I investigate next?', 'What caused this incident?'];
export function summary(metrics) {
  const count = metrics.reduce((n,m) => n + m.totalCount,0);
  const failed = metrics.reduce((n,m) => n + m.failedCount,0);
  return { count, failed, failureRate: count ? failed/count*100 : null,
    latency: count ? metrics.reduce((n,m) => n + m.averageLatencyMs*m.totalCount,0)/count : null };
}
export function orderedMetrics(metrics,currency) {
  return metrics.filter(m=>m.currency===currency).toSorted((a,b)=>new Date(a.bucket)-new Date(b.bucket));
}
export function segments(metrics) {
  const result=[];
  for(const metric of metrics) {
    const last=result.at(-1);
    if(!last || new Date(metric.bucket)-new Date(last.at(-1).bucket)>60000) result.push([metric]);
    else last.push(metric);
  }
  return result;
}
export async function request(path,options={}) {
  const controller=new AbortController();
  const abort=()=>controller.abort();
  if(options.signal?.aborted) controller.abort();
  options.signal?.addEventListener('abort',abort,{once:true});
  const timer=setTimeout(abort,options.timeout??35000);
  try {
    const response=await fetch(path,{...options,signal:controller.signal,cache:'no-store',
      headers:{'Content-Type':'application/json',...options.headers}});
    const body=await response.json().catch(()=>null);
    if(!response.ok) throw new Error(body?.detail ?? body?.message ?? `Request failed (${response.status}). Check that the pipeline is running.`);
    if(body===null) throw new Error('The server did not return JSON. Check the pipeline connection.');
    return body;
  } catch(error) {
    if(error.name==='AbortError') throw new Error('Request cancelled or timed out. Try again when the pipeline is ready.');
    throw error;
  } finally {
    clearTimeout(timer);
    options.signal?.removeEventListener('abort',abort);
  }
}
export async function loadData(source,signal) {
  if(source==='demo') return request('/demo/snapshot.json',{signal});
  const [metrics,cases,transactions,monitoring]=await Promise.all([
    request('/api/pipeline/metrics?limit=100',{signal}),request('/api/intelligence/incidents?includeCleared=true&limit=100',{signal}),
    request('/api/pipeline/transactions?limit=100',{signal}),request('/api/monitoring/status',{signal})]);
  return {capturedAt:new Date().toISOString(),metrics,cases,transactions,monitoring,reports:{},questions:{}};
}
export function savedAnswer(data,id,question) {
  const entries=Object.entries(data.questions[id]??{});
  return entries.find(([text])=>text.toLowerCase()===question.trim().toLowerCase())?.[1] ?? {
    mode:'SAVED_DEMO',answer:'This hosted demo has saved answers to the suggested questions. Choose one below, or run the local pipeline to ask a new question.',evidenceRules:[]};
}
