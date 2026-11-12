import {summary} from './data';

export function minuteState(metric) {
  if(metric.totalCount<20)return 'INSUFFICIENT_DATA';
  const failure=metric.failedCount/metric.totalCount*100;
  if(failure>=50||metric.averageLatencyMs>=2000)return 'CRITICAL';
  if(failure>=20||metric.averageLatencyMs>=1000)return 'WARNING';
  return 'NORMAL';
}

export function investigate(metrics, bucket) {
  const ordered=[...metrics].sort((a,b)=>Date.parse(a.bucket)-Date.parse(b.bucket));
  const selected=ordered.find(m=>m.bucket===bucket)??ordered.at(-1);
  if(!selected)return null;
  // Compare only observed, qualifying minutes in the preceding ten-minute window.
  const preceding=ordered.filter(m=>m.currency===selected.currency&&m.totalCount>=20&&Date.parse(m.bucket)<Date.parse(selected.bucket)&&Date.parse(m.bucket)>=Date.parse(selected.bucket)-600000).slice(-5);
  const baseline=preceding.length>=3?summary(preceding):null;
  return {selected,preceding,baseline,state:minuteState(selected),current:summary([selected]),
    flagged:ordered.filter(m=>['WARNING','CRITICAL'].includes(minuteState(m))).length,
    sparse:ordered.filter(m=>minuteState(m)==='INSUFFICIENT_DATA').length};
}

export function investigationBrief(metrics,bucket,source) {
  const result=investigate(metrics,bucket);
  if(!result)return 'No observed payment minutes.';
  const {selected,current,baseline,preceding,state}=result;
  const fmt=(v,d=1)=>v==null?'Unavailable':Number(v).toFixed(d);
  return ['# SentinelPay — Investigation brief','',`Source: ${source}`,`Currency: ${selected.currency}`,`Selected minute (UTC): ${selected.bucket}`,`Failure/latency check: ${state}`,'',
    '## Observed evidence',`- Payments: ${current.count}`,`- Failed attempts: ${current.failed}`,`- Failure rate: ${fmt(current.failureRate)}%`,`- Average latency: ${fmt(current.latency)} ms`,'',
    '## Earlier-minute comparison',baseline?`Weighted baseline across ${preceding.length} qualifying observed minutes in the preceding ten minutes: ${fmt(baseline.failureRate)}% failures; ${fmt(baseline.latency)} ms average latency.`:'Insufficient baseline: at least three preceding minutes with 20 payments each are required.',
    baseline&&current.count>=20?`Change: ${fmt(current.failureRate-baseline.failureRate)} percentage points in failures; ${fmt(current.latency-baseline.latency)} ms in latency.`:'No qualified change comparison.','',
    '## Investigation checklist','- Check payment-provider errors and timeout logs for the selected minute.','- Compare deployments or configuration changes with the observed window.','- Check adjacent minutes to distinguish a spike from sustained degradation.','',
    '## Limits','Historical aggregate evidence only. Missing minutes are unknown, not zero traffic. Checks require 20 payments; warning thresholds are 20% failures or 1,000 ms average latency; critical thresholds are 50% or 2,000 ms. This comparison is descriptive, not a forecast, confirmed root cause, or AI conclusion. Volume-drop findings remain in the backend rule results.'].join('\n');
}

export function downloadBrief(text) {
  const url=URL.createObjectURL(new Blob([text],{type:'text/markdown;charset=utf-8'}));
  const a=document.createElement('a');a.href=url;a.download='sentinelpay-investigation.md';a.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
}
