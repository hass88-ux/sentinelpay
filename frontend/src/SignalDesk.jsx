import React,{useState} from 'react';
import Chart,{clock,number} from './Chart';
import {investigate,minuteState,investigationBrief,downloadBrief} from './investigation';

const names={NORMAL:'Within thresholds',WARNING:'Warning',CRITICAL:'Critical',INSUFFICIENT_DATA:'Needs more data'};
export default function SignalDesk({metrics,source='Saved synthetic demo',onInvestigate}) {
  const [bucket,setBucket]=useState(null),[signal,setSignal]=useState('latency');
  const result=investigate(metrics,bucket);
  if(!result)return <section className="panel empty">No observed minutes to investigate.</section>;
  const {selected,current,baseline,preceding,state,flagged,sparse}=result;
  const qualified=current.count>=20;
  const delta=(value,unit)=>`${value>0?'+':''}${number(value,1)} ${unit}`;
  return <section className="panel signal-desk" aria-label="Signal desk">
    <div className="panel-heading"><div><p className="eyebrow">01 / SIGNAL DESK</p><h2>Every spike has a timeline.</h2><p className="muted">Select a minute. Compare the evidence. Decide what to investigate.</p></div><button className="button" onClick={()=>downloadBrief(investigationBrief(metrics,selected.bucket,source))}>↓ Export brief</button></div>
    <div className="desk-summary"><span><b>{metrics.length}</b> observed minutes</span><span><i className="status-dot warning"/>{flagged} failure / latency flags</span><span>{sparse} undersampled</span><span className="mono">{selected.currency} · UTC</span></div>
    <div className="desk-grid"><div className="desk-plot"><div className="chart-tabs" role="group" aria-label="Signal desk metric">{[['latency','Latency'],['failure','Failure rate'],['volume','Volume']].map(([key,label])=><button key={key} className={signal===key?'chosen':''} aria-pressed={signal===key} onClick={()=>setSignal(key)}>{label}</button>)}</div><Chart metrics={metrics} signal={signal} selectedBucket={selected.bucket} onSelect={setBucket}/>
    <div className="minute-strip" role="group" aria-label="Select an observed minute">{metrics.map(m=><button key={m.bucket} className={`minute-cell ${minuteState(m).toLowerCase()} ${selected.bucket===m.bucket?'is-selected':''}`} aria-pressed={selected.bucket===m.bucket} aria-label={`${clock(m.bucket)} UTC, ${names[minuteState(m)]}, ${m.totalCount} payments`} onClick={()=>setBucket(m.bucket)}><span className="minute-bar"/><span>{clock(m.bucket)}</span></button>)}</div><p className="desk-caption">Observed minutes only · amber / red indicate threshold crossings · gray needs data</p></div>
    <aside className="minute-inspector"><div className="inspector-heading"><span className="eyebrow">SELECTED MINUTE</span><strong>{clock(selected.bucket)} <small>UTC</small></strong><span className={`severity ${state==='CRITICAL'?'critical':state==='WARNING'?'warning':state==='NORMAL'?'normal':'neutral'}`}>{names[state]}</span><small>{selected.bucket.slice(0,10)} · {source}</small></div>
    <dl className="minute-facts"><div><dt>Payments</dt><dd>{number(current.count)}<small>{number(current.failed)} failed</small></dd></div><div><dt>Failure rate</dt><dd>{current.failureRate==null?'—':`${number(current.failureRate,1)}%`}<small>{baseline&&qualified?delta(current.failureRate-baseline.failureRate,'pp vs baseline'):'No qualified comparison'}</small></dd></div><div><dt>Average latency</dt><dd>{number(current.latency)} ms<small>{baseline&&qualified?delta(current.latency-baseline.latency,'ms vs baseline'):'No qualified comparison'}</small></dd></div></dl>
    <p className="baseline-note">{baseline?`Baseline: ${preceding.length} preceding qualifying minutes, weighted by payment count.`:'Baseline needs 3 earlier minutes with at least 20 payments each, within the preceding 10 minutes.'}</p>
    {onInvestigate&&<button className="button primary" onClick={()=>onInvestigate(selected.bucket)}>Investigate this minute →</button>}
    </aside></div><details className="desk-method"><summary>How to read these signals</summary><p>Failure and latency checks require 20 payments in a minute. Warning: ≥20% failures or ≥1,000 ms average latency. Critical: ≥50% or ≥2,000 ms. A minute within these thresholds is not proof that the whole system is healthy. Volume rules are evaluated separately by the backend. Comparisons use up to five qualifying earlier minutes; missing minutes are never filled with zeros.</p></details>
  </section>;
}
