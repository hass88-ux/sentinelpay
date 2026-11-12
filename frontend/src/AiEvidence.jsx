import React from 'react';
import {number} from './Chart';

export default function AiEvidence({answer}) {
  if(!answer.evidenceReferences)return null;
  const quality=answer.dataQuality;
  return <div className="ai-grounding"><div className="grounding-label">EVIDENCE RECEIPT <span>Values calculated by the server</span></div>
    {quality&&<p className="grounding-quality">{quality.observedMinutes} observed minutes · {quality.missingMinutes} missing minutes · {quality.undersampledMinutes} undersampled<br/>Root cause: not established</p>}
    <details><summary>Inspect the evidence behind this answer ({answer.evidenceReferences.length})</summary><div className="grounding-records">{answer.evidenceReferences.map(r=><article key={r.id}><strong>{r.id} · {r.id==='WINDOW'?'Whole observed window':r.id==='COMPARISON'?'Earlier-minute comparison':`${r.bucket.replace('T',' ').slice(0,16)} UTC`}</strong>
    {r.id==='COMPARISON'?<><p>Selected minute: {r.selectedBucket}</p><p>{r.state==='AVAILABLE'?`${number(r.failureRateChangePp,2)} percentage-point change in failures; ${number(r.latencyChangeMs,2)} ms change in average latency.`:'Not enough qualifying evidence for this comparison.'}</p><small>Baseline minutes: {r.baselineMinutes.join(', ')||'None'}</small></>:<><p>{number(r.totalCount)} payments · {number(r.failedCount)} failed<br/>{r.failureRatePct==null?'Failure rate unavailable':`${number(r.failureRatePct,2)}% failure rate`} · {number(r.averageLatencyMs,2)} ms average latency</p>{r.from&&<small>{r.from} — {r.to}</small>}{r.sampleStatus==='INSUFFICIENT_DATA'&&<small>Fewer than 20 payments: insufficient for failure/latency rule conclusions.</small>}</>}
    </article>)}</div></details><p className="grounding-limit">References are checked against the supplied dataset. This does not prove the AI’s interpretation or establish a root cause.</p></div>;
}
