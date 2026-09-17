import React,{useEffect,useRef,useState} from 'react';

export default function UploadAi({id,currency,getToken}) {
  const [consent,setConsent]=useState(false),[question,setQuestion]=useState('What should I investigate in this file?');
  const [busy,setBusy]=useState(false),[answer,setAnswer]=useState(null),[error,setError]=useState('');
  const active=useRef(null);
  useEffect(()=>()=>active.current?.abort(),[]);
  async function ask(e) {
    e.preventDefault();if(!consent||busy)return;
    const abort=new AbortController();active.current=abort;setBusy(true);setError('');setAnswer(null);
    try {
      const token=await getToken();if(abort.signal.aborted)return;
      const response=await fetch(`/api/uploads/${id}/questions`,{method:'POST',cache:'no-store',
        headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},
        body:JSON.stringify({question,currency,consent:true}),signal:AbortSignal.any([abort.signal,AbortSignal.timeout(90000)])});
      const result=await response.json();
      if(!response.ok)throw new Error(result.detail??'AI is unavailable. Your metrics still work.');
      if(!abort.signal.aborted)setAnswer(result);
    }catch(e){if(!abort.signal.aborted)setError(e.name==='TimeoutError'?'AI timed out. Please try later.':e.message)}
    finally{if(!abort.signal.aborted)setBusy(false)}
  }
  return <section className="question-box"><h3>Ask AI about this file</h3>
    <p className="muted">Groq receives your question and this file’s {currency} minute-level counts, failures, latencies, and rule findings. Transaction IDs, amounts, filenames, and account details are excluded. Do not include confidential information in your question.</p>
    <form onSubmit={ask}><label><input type="checkbox" checked={consent} disabled={busy} onChange={e=>setConsent(e.target.checked)}/> I agree to send this summary and my question to Groq.</label>
      <label htmlFor="upload-question">Question</label><textarea id="upload-question" rows={3} maxLength={500} value={question} onChange={e=>setQuestion(e.target.value)} required/>
      <p className="muted">Up to 120 observed minutes per currency. 3 questions/minute and 20/day per account, subject to shared capacity.</p>
      <button className="button primary" disabled={!consent||busy||!question.trim()}>{busy?'Analyzing…':'Ask AI'}</button>
    </form>{error&&<p className="notice" role="alert">{error}</p>}
    {answer&&<div className="answer" role="status"><span className="answer-label">LIVE AI · UNVERIFIED</span><p>{answer.answer}</p><div className="citations">{answer.evidenceRules.map(r=><span key={r}>{r.replaceAll('_',' ')}</span>)}</div><ul>{answer.nextChecks.map(c=><li key={c}>{c}</li>)}</ul><small>{answer.notice}</small></div>}
  </section>;
}
