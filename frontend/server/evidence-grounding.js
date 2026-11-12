// All arithmetic and reference labels are produced here, never by the model.
const round=value=>value==null?null:Math.round(value*100)/100;
function aggregate(rows) {
  const count=rows.reduce((n,r)=>n+r.totalCount,0),failed=rows.reduce((n,r)=>n+r.failedCount,0);
  return {totalCount:count,failedCount:failed,failureRatePct:count?round(failed/count*100):null,
    averageLatencyMs:count?round(rows.reduce((n,r)=>n+r.averageLatencyMs*r.totalCount,0)/count):null};
}
export function groundEvidence(facts) {
  const rows=facts.observations;
  if(!/^[A-Z]{3}$/.test(facts.currency)||!Array.isArray(rows)||!rows.length||rows.length>120)
    throw new Error('Evidence must contain one currency and 1–120 observed minutes.');
  const seen=new Set();
  for(const r of rows) {
    const time=Date.parse(r.bucket);
    if(!Number.isFinite(time)||time%60000!==0||seen.has(time)||!Number.isSafeInteger(r.totalCount)||r.totalCount<0||
      !Number.isSafeInteger(r.failedCount)||r.failedCount<0||r.failedCount>r.totalCount||
      typeof r.averageLatencyMs!=='number'||!Number.isFinite(r.averageLatencyMs)||r.averageLatencyMs<0)
      throw new Error('Evidence contains invalid or duplicate minute metrics.');
    seen.add(time);
  }
  const ordered=[...rows].sort((a,b)=>Date.parse(a.bucket)-Date.parse(b.bucket));
  const references=ordered.map((r,i)=>({id:`M${i+1}`,bucket:new Date(r.bucket).toISOString(),...aggregate([r]),
    sampleStatus:r.totalCount>=20?'QUALIFIED':'INSUFFICIENT_DATA'}));
  const summary={id:'WINDOW',from:references[0].bucket,to:references.at(-1).bucket,observedMinutes:rows.length,...aggregate(rows)};
  let missingMinutes=0;
  for(let i=1;i<ordered.length;i++)missingMinutes+=(Date.parse(ordered[i].bucket)-Date.parse(ordered[i-1].bucket))/60000-1;
  const selected=facts.bucket?ordered.find(r=>Date.parse(r.bucket)===Date.parse(facts.bucket)):ordered.at(-1);
  if(!selected)throw new Error('The incident minute is missing from the evidence.');
  const before=ordered.filter(r=>r.totalCount>=20&&Date.parse(r.bucket)<Date.parse(selected.bucket)&&Date.parse(r.bucket)>=Date.parse(selected.bucket)-600000).slice(-5);
  const baseline=before.length>=3?aggregate(before):null;
  const current=aggregate([selected]);
  const comparison={id:'COMPARISON',selectedBucket:selected.bucket,baselineMinutes:before.map(r=>r.bucket),
    state:baseline&&selected.totalCount>=20?'AVAILABLE':'INSUFFICIENT_DATA',baseline,
    failureRateChangePp:baseline&&selected.totalCount>=20?round(current.failureRatePct-baseline.failureRatePct):null,
    latencyChangeMs:baseline&&selected.totalCount>=20?round(current.averageLatencyMs-baseline.averageLatencyMs):null};
  const quality={observedMinutes:rows.length,missingMinutes,undersampledMinutes:rows.filter(r=>r.totalCount<20).length,
    rootCause:'UNKNOWN',scope:'Historical aggregate metrics; no provider logs, traces, deployments, or processor identifiers.'};
  const evaluations=facts.evidence??facts.findings?.flatMap(f=>f.evaluations.map(e=>({...e,bucket:f.bucket})))??[];
  const ruleChecks=evaluations.map(e=>({rule:e.rule,bucket:e.bucket??facts.bucket,state:e.state,observed:e.observed,
    warningThreshold:e.warningThreshold,criticalThreshold:e.criticalThreshold,
    criticalRelation:typeof e.observed==='number'&&typeof e.criticalThreshold==='number'?e.observed===e.criticalThreshold?'EQUAL_TO':e.observed<e.criticalThreshold?'BELOW':'ABOVE':'NOT_COMPARABLE',
    breachDirection:e.rule==='VOLUME_DROP'?'AT_OR_BELOW':'AT_OR_ABOVE'}));
  return {references,summary,comparison,quality,ruleChecks};
}

export function resolveReferences(ids,grounded) {
  const allowed=[...grounded.references,grounded.summary,grounded.comparison];
  if(!Array.isArray(ids)||ids.length<1||ids.length>6||ids.some(id=>typeof id!=='string'||!allowed.some(r=>r.id===id)))
    throw new Error('The model cited a minute or comparison outside the supplied evidence.');
  return [...new Set(ids)].map(id=>allowed.find(r=>r.id===id));
}
