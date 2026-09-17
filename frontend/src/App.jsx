import React,{useEffect,useRef,useState} from 'react';
import Chart,{clock,number} from './Chart';
import Accounts from './Accounts';
import {loadData,orderedMetrics,request,savedAnswer,starterQuestions,summary} from './data';

const labels={CRITICAL:'Critical',WARNING:'Warning',NORMAL:'Normal',NONE:'None',ALERT:'Alert',CLEARED:'Cleared',UNDETERMINED:'Needs data',INSUFFICIENT_DATA:'Needs data',SUCCESS:'Success',FAILED:'Failed'};
const ruleLabels={FAILURE_RATE:'Failure rate',AVERAGE_LATENCY:'Average latency',VOLUME_DROP:'Volume drop'};
function Badge({value}) {return <span className={`severity ${['CRITICAL','FAILED'].includes(value)?'critical':value==='WARNING'?'warning':value==='NORMAL'||value==='CLEARED'||value==='SUCCESS'?'normal':'neutral'}`}>{labels[value]??value}</span>}
function Empty({children}) {return <div className="empty">{children}</div>}

export default function App() {
  const liveAvailable=import.meta.env.DEV||import.meta.env.VITE_LIVE_ENABLED==='true'||['localhost','127.0.0.1'].includes(location.hostname);
  const [source,setSource]=useState(import.meta.env.VITE_DEFAULT_SOURCE==='live'?'live':'demo');
  const [view,setView]=useState(new URLSearchParams(location.search).has('account')?'My uploads':'Overview'),[data,setData]=useState(null),[currency,setCurrency]=useState('USD');
  const [loading,setLoading]=useState(true),[error,setError]=useState(''),[refresh,setRefresh]=useState(0);
  const [signal,setSignal]=useState('latency'),[selected,setSelected]=useState(null),[report,setReport]=useState(null);
  const [reportError,setReportError]=useState(''),[forecasts,setForecasts]=useState([]),[forecastError,setForecastError]=useState('');
  const [question,setQuestion]=useState(''),[answer,setAnswer]=useState(null),[asking,setAsking]=useState(false),[questionError,setQuestionError]=useState('');
  const [scenario,setScenario]=useState('DEGRADED'),[action,setAction]=useState(''),[notice,setNotice]=useState('');
  const [caseFilter,setCaseFilter]=useState('ALL'),[paymentFilter,setPaymentFilter]=useState('ALL'),[search,setSearch]=useState('');
  const questionController=useRef(null),actionController=useRef(null);
  const [demoAi,setDemoAi]=useState(false),[answerMode,setAnswerMode]=useState('ai');
  const liveDemoAi=source==='demo'&&demoAi&&answerMode==='ai';
  useEffect(()=>{const controller=new AbortController(); request('/api/demo/ai/status',{signal:controller.signal}).then(s=>{if(!controller.signal.aborted)setDemoAi(s.enabled===true)}).catch(()=>{}); return()=>controller.abort()},[]);

  useEffect(()=>{
    const controller=new AbortController();
    setLoading(true);setError('');setData(null);setSelected(null);setAnswer(null);setNotice('');
    loadData(source,controller.signal).then(result=>{if(!controller.signal.aborted)setData(result)}).catch(ex=>{if(!controller.signal.aborted)setError(ex.message)}).finally(()=>{if(!controller.signal.aborted)setLoading(false)});
    return ()=>controller.abort();
  },[source,refresh]);
  useEffect(()=>{actionController.current?.abort();setAction('');return()=>actionController.current?.abort()},[source]);
  useEffect(()=>{
    const controller=new AbortController();
    setReport(null);setReportError('');setAnswer(null);setQuestion('');setQuestionError('');setAsking(false);
    questionController.current?.abort();
    if(selected&&data) {
      const result=source==='demo'?Promise.resolve(data.reports[selected]):request(`/api/intelligence/incidents/${encodeURIComponent(selected)}`,{signal:controller.signal});
      result.then(value=>{if(!controller.signal.aborted){if(!value)throw new Error('This case is not in the saved sample.');setReport(value)}}).catch(ex=>{if(!controller.signal.aborted)setReportError(ex.message)});
    }
    return()=>{controller.abort();questionController.current?.abort()};
  },[selected,source,data]);
  useEffect(()=>{
    const controller=new AbortController();setForecasts([]);setForecastError('');
    if(data) {
      if(source==='demo') setForecasts(currency==='USD'?data.forecasts:[]);
      else {
        const cutoff=Math.floor((Date.now()-10000)/60000)*60000-60000;
        const eligible=orderedMetrics(data.metrics,currency).filter(m=>+new Date(m.bucket)<=cutoff);
        const asOf=eligible.at(-1)?.bucket;
        request(`/api/intelligence/forecasts?currency=${currency}${asOf?`&asOf=${encodeURIComponent(asOf)}`:''}`,{signal:controller.signal})
          .then(value=>{if(!controller.signal.aborted)setForecasts(value)}).catch(ex=>{if(!controller.signal.aborted)setForecastError(ex.message)});
      }
    }
    return()=>controller.abort();
  },[data,source,currency]);

  const metrics=orderedMetrics(data?.metrics??[],currency),totals=summary(metrics);
  const cases=(data?.cases??[]).filter(c=>c.currency===currency);
  const visibleCases=cases.filter(c=>caseFilter==='ALL'||c.state===caseFilter);
  const currencies=[...new Set(['USD',...(data?.metrics??[]).map(m=>m.currency),...(data?.cases??[]).map(c=>c.currency)])].sort();
  const payments=(data?.transactions??[]).filter(p=>p.currency===currency&&(paymentFilter==='ALL'||p.status===paymentFilter)&&p.id.toLowerCase().includes(search.toLowerCase()));
  const forecast=forecasts.find(f=>f.signal==='AVERAGE_LATENCY');
  const featuredCase=cases[0];
  const caseMetric=metrics.find(m=>featuredCase && +new Date(m.bucket)===+new Date(featuredCase.bucket));

  async function runAction(kind) {
    const controller=new AbortController();actionController.current?.abort();actionController.current=controller;
    setAction(kind);setNotice('');
    try {
      if(kind==='publish') {
        const result=await request(`/api/pipeline/simulations?count=100&scenario=${scenario}`,{method:'POST',signal:controller.signal});
        if(!controller.signal.aborted)setNotice(`${result.acknowledgedIds.length} payments acknowledged by Kafka. Refresh after storage completes; monitoring waits for the minute to close.`);
      } else {
        const result=await request('/api/monitoring/runs',{method:'POST',signal:controller.signal});
        if(!controller.signal.aborted)setNotice(`Scan ${result.state.toLowerCase()}: ${result.observedMinutes} observed minute/currency pairs, ${result.alertEvaluations} alert evaluations. Refresh to read the updated cases.`);
      }
    } catch(ex){if(!controller.signal.aborted)setNotice(ex.message)} finally{if(!controller.signal.aborted)setAction('')}
  }
  async function ask(text=question) {
    const trimmed=text.trim();if(!trimmed||trimmed.length>500||!selected||!report)return;
    questionController.current?.abort();const controller=new AbortController();questionController.current=controller;
    setQuestion(trimmed);setAsking(true);setQuestionError('');setAnswer(null);
    try {
      const result=liveDemoAi?await request(`/api/demo/incidents/${encodeURIComponent(selected)}/questions`,{method:'POST',body:JSON.stringify({question:trimmed}),signal:controller.signal}):source==='demo'?savedAnswer(data,selected,trimmed):await request(`/api/intelligence/incidents/${encodeURIComponent(selected)}/questions`,{method:'POST',body:JSON.stringify({question:trimmed}),signal:controller.signal});
      if(!controller.signal.aborted)setAnswer(result);
    }catch(ex){if(!controller.signal.aborted)setQuestionError(ex.message)}finally{if(!controller.signal.aborted)setAsking(false)}
  }
  function openCase(id) {setView('Incidents');setSelected(id)}
  useEffect(()=>{
    const context=document.modelContext;if(!context?.registerTool)return;
    const lifecycle=new AbortController();
    Promise.resolve(context.registerTool({name:'open_incident_case',title:'Open an incident case',description:'Select an available incident in the dashboard. Does not modify stored data.',
      inputSchema:{type:'object',properties:{id:{type:'string'}},required:['id'],additionalProperties:false},
      annotations:{readOnlyHint:true,untrustedContentHint:false},execute:async(input)=>{
        if(!input||typeof input.id!=='string'||!(data?.cases??[]).some(c=>c.id===input.id))throw new Error('Choose an incident ID from the current dataset.');
        const incident=data.cases.find(c=>c.id===input.id);setCurrency(incident.currency);openCase(input.id);
        await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
        return {selectedId:input.id,view:'Incidents'};
      }},{signal:lifecycle.signal})).catch(()=>{});
    return()=>lifecycle.abort();
  },[data]);

  return <div className="app"><aside className="sidebar"><a className="brand" href="#" onClick={e=>{e.preventDefault();setView('Overview')}}><img src="/favicon.svg" alt=""/>SentinelPay</a>
    <div className="workspace">PAYMENT OPERATIONS</div><nav aria-label="Main navigation">{['Overview','Transactions','Incidents','My uploads'].map((name,i)=><button key={name} aria-current={view===name?'page':undefined} className={view===name?'active':''} onClick={()=>setView(name)}><span aria-hidden="true">{['◫','⇄','◇','↑'][i]}</span>{name}</button>)}</nav>
    <div className="sidebar-footer"><span className="system-mark">SP</span><div>{source==='demo'?'Demo workspace':'Local workspace'}<small>Java · Kafka · PostgreSQL</small></div></div></aside>
    <main><header><div className="breadcrumb"><strong>SentinelPay</strong> <span>/</span> {view}</div><div className="header-controls"><label className="sr-only" htmlFor="source">Data source</label>{liveAvailable?<select id="source" value={source} onChange={e=>setSource(e.target.value)}><option value="demo">Saved demo</option><option value="live">Live pipeline</option></select>:<span className="source-badge">Saved demo</span>}</div></header>
    <div hidden={view!=='My uploads'}>{view==='My uploads'&&<Accounts/>}</div><div className="page" hidden={view==='My uploads'}><div className="title-row"><div><p className="eyebrow">{view==='Overview'?'SYSTEM OVERVIEW':view==='Transactions'?'PAYMENT ACTIVITY':'INCIDENT INVESTIGATION'}</p><h1>{view==='Overview'?'Payment operations':view==='Transactions'?'Transactions':'Incident inbox'}</h1><p className="muted">{view==='Overview'?'Trace payment failures, latency, and the evidence behind each alert.':view==='Transactions'?'Inspect the latest stored payment attempts.':'Follow the evidence from alert to explanation.'}</p></div><div className="title-controls"><label className="sr-only" htmlFor="currency">Currency</label><select id="currency" value={currency} onChange={e=>{setCurrency(e.target.value);setSelected(null)}}>{currencies.map(c=><option key={c}>{c}</option>)}</select><button className="button" disabled={loading||!!action} onClick={()=>setRefresh(n=>n+1)}>{loading?'Loading…':'↻ Refresh'}</button></div></div>
    <div className="demo-banner"><strong>{source==='demo'?'Demo dataset':'Live pipeline'}</strong><span>{source==='demo'?(demoAi?'Saved synthetic traffic. Live AI can explain this evidence.':'Saved synthetic traffic. Questions use recorded backend answers.'):'Reads the Java backend. Counts reflect the returned metric window.'}</span><span className="mono">{currency} · UTC</span></div>
    {source==='live'&&<div className="actions"><label htmlFor="scenario">Traffic scenario</label><select id="scenario" value={scenario} onChange={e=>setScenario(e.target.value)}>{['NORMAL','DEGRADED','OUTAGE'].map(s=><option key={s}>{s}</option>)}</select><button className="button" disabled={!!action||loading||!!error} onClick={()=>runAction('publish')}>{action==='publish'?'Publishing…':'Generate 100 payments'}</button><button className="button" disabled={!!action||loading||!!error} onClick={()=>runAction('scan')}>{action==='scan'?'Scanning…':'Run monitoring'}</button><span className="muted">Scan: {data?.monitoring?.state??'—'}</span></div>}
    {notice&&<p className="notice" role="status">{notice}</p>}
    {error?<section className="panel error-state" role="alert"><h2>Couldn’t load the {source==='demo'?'saved demo':'pipeline'}</h2><p>{error}</p>{source==='live'&&<p>Start the Java demo on port 8082, then refresh. You can also switch to the saved demo.</p>}<button className="button" onClick={()=>setRefresh(n=>n+1)}>Retry</button></section>:loading?<section className="panel empty" role="status">Loading payment data…</section>:<>
    {view==='Overview'&&<><section className="stats" aria-label="Payment summary">{[['Payments',number(totals.count),`${metrics.length} observed minutes`],['Failure rate',totals.failureRate==null?'—':`${number(totals.failureRate,1)}%`,`${number(totals.failed)} failed attempts`],['Average latency',totals.latency==null?'—':`${number(totals.latency)} ms`,'Weighted across attempts'],['Incident cases',number(cases.length),`${cases.filter(c=>c.severity==='CRITICAL').length} critical · historical cases`]].map(([label,value,note])=><article className="stat" key={label}><span>{label}</span><strong>{value}</strong><small>{note}</small></article>)}</section>
    <div className="overview-grid"><section className="panel chart-panel"><div className="panel-heading"><div><h2>{signal==='latency'?'Latency over time':signal==='failure'?'Failure rate over time':'Payment volume'}</h2><p className="muted">{source==='demo'?'Six-minute synthetic scenario':'Latest returned metric window'}</p></div><span className="legend"><i/>Observed metrics</span></div><div className="chart-tabs" role="group" aria-label="Chart metric">{[['latency','Latency'],['failure','Failure rate'],['volume','Volume']].map(([key,label])=><button key={key} aria-pressed={signal===key} className={signal===key?'chosen':''} onClick={()=>setSignal(key)}>{label}</button>)}</div><Chart metrics={metrics} signal={signal}/></section>
    <aside className="overview-side"><section className="panel featured-case"><div className="report-title"><span className="eyebrow">LATEST CASE</span>{featuredCase&&<Badge value={featuredCase.severity}/>}</div>{featuredCase?<><h2>Payment degradation</h2><p className="muted">{featuredCase.currency} · {clock(featuredCase.bucket)} UTC · Historical case</p><div className="featured-evidence"><div><span>Average latency</span><strong>{number(caseMetric?.averageLatencyMs)} ms</strong></div><div><span>Failure rate</span><strong>{caseMetric?.totalCount?`${number(caseMetric.failedCount/caseMetric.totalCount*100,1)}%`:"—"}</strong></div></div><button className="text-button" onClick={()=>openCase(featuredCase.id)}>Open investigation →</button></>:<Empty>No recorded case for this currency.</Empty>}</section><section className="panel forecast"><p className="eyebrow">TREND FORECAST</p><div className="risk-label">{forecast?.state==='THRESHOLD_RISK'?'Threshold crossing projected':forecast?.state==='ALREADY_ELEVATED'?'Already above threshold':forecast?.state==='UNSTABLE_TREND'?'Unstable trend':forecast?.state==='NO_CROSSING_PROJECTED'?'No crossing projected':'Insufficient data'}</div><h2>{forecast?.state==='THRESHOLD_RISK'?'A rising trend before the spike.':'Look beyond the latest minute.'}</h2><p>{forecastError||'Five qualifying minutes, projected three minutes ahead.'}</p><div className="projection"><strong>{number(forecast?.projectedValue)} <small>ms</small></strong><span>projected average latency</span></div><div className="forecast-footer">{forecast?.asOf?`As of ${clock(forecast.asOf)} UTC · `:''}Linear baseline<br/>Historical fit: {number(forecast?.fitRSquared,2)} R²<br/>A projection, not an incident probability.</div></section></aside></div>
    <section className="panel incidents"><div className="panel-heading"><div><h2>Incident inbox <span className="count">{cases.length}</span></h2><p className="muted">Related findings, grouped by minute and currency</p></div><button className="text-button" onClick={()=>setView('Incidents')}>View cases →</button></div>{cases.length?cases.slice(0,5).map(c=><CaseRow key={c.id} incident={c} onClick={()=>openCase(c.id)}/>):<Empty>No recorded incident cases for this currency.</Empty>}</section></>}
    {view==='Transactions'&&<section className="panel"><div className="panel-heading wrap"><div><h2>Stored payments</h2><p className="muted">Latest {data.transactions.length} returned · {payments.length} match your filters</p></div><div className="filters"><label className="sr-only" htmlFor="payment-search">Search payment ID</label><input id="payment-search" placeholder="Search payment ID" value={search} onChange={e=>setSearch(e.target.value)}/><label className="sr-only" htmlFor="payment-status">Payment status</label><select id="payment-status" value={paymentFilter} onChange={e=>setPaymentFilter(e.target.value)}><option value="ALL">All statuses</option><option>SUCCESS</option><option>FAILED</option></select></div></div>{payments.length?<div className="table-scroll"><table><thead><tr><th>Payment ID</th><th>Time (UTC)</th><th>Amount</th><th>Status</th><th>Latency</th></tr></thead><tbody>{payments.map(p=><tr key={p.id}><td><code className="payment-id" title={p.id}>{p.id}</code></td><td>{clock(p.timestamp)}</td><td>{number(p.amount,2)} {p.currency}</td><td><Badge value={p.status}/></td><td>{number(p.latencyMs)} ms</td></tr>)}</tbody></table></div>:<Empty>No payments match these filters.</Empty>}</section>}
    {view==='Incidents'&&<div className={`investigation-grid ${selected?'has-selection':''}`}><section className="panel case-list"><div className="panel-heading wrap"><h2>{visibleCases.length} {visibleCases.length===1?'case':'cases'}</h2><label className="sr-only" htmlFor="case-status">Case status</label><select id="case-status" value={caseFilter} onChange={e=>setCaseFilter(e.target.value)}><option value="ALL">All cases</option><option value="ALERT">Alert</option><option value="CLEARED">Cleared</option><option value="UNDETERMINED">Needs data</option></select></div>{visibleCases.length?visibleCases.map(c=><CaseRow key={c.id} incident={c} selected={selected===c.id} onClick={()=>setSelected(c.id)}/>):<Empty>No cases match this filter.</Empty>}</section>
    {selected&&<section className="panel investigation" aria-label="Incident details"><div className="panel-heading"><h2>Investigation</h2><button className="text-button" onClick={()=>setSelected(null)}>Close</button></div>{reportError?<div className="empty" role="alert">{reportError}</div>:!report?<Empty>Loading evidence…</Empty>:<div className="report-body"><div className="report-title"><Badge value={report.incident.severity}/><span>{clock(report.incident.bucket)} UTC · {report.incident.currency}</span><Badge value={report.incident.state}/></div><h2>Payment degradation</h2><p className="muted">{new Date(report.incident.bucket).toLocaleDateString('en-GB',{timeZone:'UTC'})} · Historical case</p><p className="report-summary">{report.summary}</p><h3>Evidence</h3><div className="evidence-list">{report.evidence.map(e=><article key={e.rule}><div><strong>{ruleLabels[e.rule]??e.rule}</strong><Badge value={e.state}/></div><p className="evidence-value">{number(e.observed,1)}{e.rule==='FAILURE_RATE'?'%':e.rule==='AVERAGE_LATENCY'?' ms':' payments'}</p><small>{e.sampleCount} samples · warning {number(e.warningThreshold,1)} · critical {number(e.criticalThreshold,1)}</small><p className="muted">{e.explanation}</p></article>)}</div><h3>Suggested investigation</h3><ul className="checks">{report.nextChecks.map(check=><li key={check}>{check}</li>)}</ul>
    <div className="question-box"><div className="question-heading"><h3>Ask about this incident</h3><span>{liveDemoAi?'Live AI · Groq':source==='demo'?'Saved answers':'Evidence-based answers'}</span></div><p className="muted">{liveDemoAi?'Your question and this case’s aggregate evidence are sent to Groq. Do not include personal or confidential information.':source==='demo'?'Explore answers recorded by the Java backend.':'Questions use this case only. AI is optional; basic answers work without a model.'}</p><div className="answer-controls">{source==='demo'&&demoAi&&<><label htmlFor="answer-mode">Answer source</label><select id="answer-mode" value={answerMode} onChange={e=>{questionController.current?.abort();setAsking(false);setAnswerMode(e.target.value);setAnswer(null);setQuestionError('')}}><option value="ai">Live AI (Groq)</option><option value="saved">Saved backend answers</option></select></>}</div><div className="suggestions">{starterQuestions.slice(0,3).map(text=><button key={text} disabled={asking} onClick={()=>ask(text)}>{text}</button>)}</div><form onSubmit={e=>{e.preventDefault();ask()}}><label className="sr-only" htmlFor="question">Incident question</label><textarea id="question" value={question} onChange={e=>setQuestion(e.target.value)} maxLength={500} rows={3} placeholder="What should I investigate next?"/><div className="question-submit"><span>{question.length}/500 · One case, no saved chat</span><button className="button primary" disabled={asking||!question.trim()}>{asking?'Getting answer…':liveDemoAi?'Ask AI':'Ask question'}</button></div></form>{questionError&&<p className="notice" role="alert">{questionError}</p>}{answer&&<div className="answer" role="status"><span className="answer-label">{answer.mode==='AI_ASSISTED'?'LIVE AI · UNVERIFIED':answer.mode==='SAVED_DEMO'?'DEMO LIMITATION':source==='demo'?'SAVED BACKEND RESPONSE':'RULE-BASED RESPONSE'}</span><p>{answer.answer}</p>{answer.evidenceRules?.length>0&&<div className="citations">{answer.evidenceRules.map(r=><span key={r}>{ruleLabels[r]??r}</span>)}</div>}{answer.nextChecks?.length>0&&<><h3>Suggested checks</h3><ul className="checks">{answer.nextChecks.map(check=><li key={check}>{check}</li>)}</ul></>}{(answer.notice||answer.explanation?.notice)&&<small>{answer.notice||answer.explanation.notice}</small>}</div>}</div><details className="limitations"><summary>Context and limits</summary><ul>{report.limitations.map(text=><li key={text}>{text}</li>)}</ul><p>Case ID: <code>{report.incident.id}</code></p></details></div>}</section>}</div>}
    </>}
    <footer>SentinelPay <span>{data?`Snapshot ${new Date(data.capturedAt).toLocaleString('en-GB',{timeZone:'UTC'})} UTC`:'Evidence first'}</span></footer></div></main></div>;
}
function CaseRow({incident,selected,onClick}) {return <button className={`incident-row ${selected?'selected':''}`} onClick={onClick}><Badge value={incident.severity}/><div><strong>Payment degradation</strong><small>{incident.currency} · {clock(incident.bucket)} UTC</small></div><span className="case-state">{labels[incident.state]??incident.state}</span><span aria-hidden="true">→</span></button>}
