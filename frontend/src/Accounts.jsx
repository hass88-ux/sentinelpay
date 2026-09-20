import React, {useEffect, useRef, useState} from 'react';
import {createClient} from '@supabase/supabase-js';
import Chart from './Chart';
import {summary} from './data';
import {uploadRequest} from './uploads';
import {startGoogleSignIn} from './google-auth';
import UploadAi from './UploadAi';
import {downloadSample} from './sample-csv';

export default function Accounts() {
  const [client,setClient]=useState(null),[config,setConfig]=useState(null),[session,setSession]=useState(null);
  const [email,setEmail]=useState(''),[password,setPassword]=useState(''),[mode,setMode]=useState('signin');
  const [busy,setBusy]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState('');
  const [files,setFiles]=useState([]),[analysis,setAnalysis]=useState(null),[currency,setCurrency]=useState('USD');
  const [filesState,setFilesState]=useState('loading');
  const [fileSearch,setFileSearch]=useState('');
  const [emailExpanded,setEmailExpanded]=useState(false);
  const [pendingDelete,setPendingDelete]=useState(null),[file,setFile]=useState(null);
  const generation=useRef(0), fileInput=useRef(null), currentOwner=useRef(null);
  useEffect(()=>{
    const abort=new AbortController(); let subscription, auth;
    fetch('/api/account-config',{signal:abort.signal}).then(r=>{if(!r.ok)throw new Error('Account service is unavailable.');return r.json()}).then(c=>{
      if(abort.signal.aborted)return;
      if(!c.enabled)throw new Error('Private accounts are being connected. The demo is still available.');
      setConfig(c);
      auth=createClient(c.supabaseUrl,c.publishableKey,{auth:{flowType:'pkce',persistSession:true,storage:window.sessionStorage}});
      setClient(auth);
      subscription=auth.auth.onAuthStateChange((event,s)=>{
        if(currentOwner.current!==s?.user.id){generation.current++;setAnalysis(null);setFiles([]);setFilesState('loading');setPendingDelete(null);setBusy(false);currentOwner.current=s?.user.id;}
        setSession(s);
        if(event==='PASSWORD_RECOVERY')setMode('newpassword');
      }).data.subscription;
      // The SDK exchanges the returned PKCE code automatically and removes it from the URL.
      auth.auth.getSession().then(({error})=>{if(error&&!abort.signal.aborted)setError('Sign-in did not finish. Please try again in this browser tab.');})
        .catch(()=>{if(!abort.signal.aborted)setError('Sign-in did not finish. Please try again.');});
    }).catch(e=>{if(!abort.signal.aborted)setError(e.message)});
    return()=>{abort.abort();subscription?.unsubscribe();auth?.auth.stopAutoRefresh();generation.current++};
  },[]);
  async function api(path='',options={}) {
    const {data:{session:current}}=await client.auth.getSession();
    if(!current)throw new Error('Sign in again to continue.');
    return uploadRequest(config.apiBase,current.access_token,path,options);
  }
  useEffect(()=>{
    if(!session||!client)return;
    const revision=generation.current;
    let active=true;
    setFilesState('loading');
    api().then(f=>{if(active&&revision===generation.current){setFiles(f);setFilesState('ready')}})
      .catch(e=>{if(active&&revision===generation.current){setError(e.message);setFilesState('error')}});
    return()=>{active=false};
  },[session,client]);
  async function authenticate(e) {
    e.preventDefault();setBusy(true);setError('');setMessage('');
    try {
      let result;
      const redirect=location.origin+'/?account=1';
      if(mode==='signup')result=await client.auth.signUp({email,password,options:{emailRedirectTo:redirect}});
      else if(mode==='reset')result=await client.auth.resetPasswordForEmail(email,{redirectTo:redirect});
      else if(mode==='newpassword')result=await client.auth.updateUser({password});
      else result=await client.auth.signInWithPassword({email,password});
      if(result.error)throw result.error;
      setPassword('');
      setMessage(mode==='signup'?'Check your email to confirm your account.':mode==='reset'?'If the account exists, check your email for a reset link.':mode==='newpassword'?'Password updated.':'');
      if(mode==='newpassword')setMode('signin');
    } catch(e){setError(e.message)} finally{setBusy(false)}
  }
  async function googleSignIn() {
    setBusy(true);setError('');setMessage('');
    try {await startGoogleSignIn(client,location.origin)}
    catch(e){setError(e.message);setBusy(false)}
  }
  async function act(task) {
    const revision=generation.current;setBusy(true);setError('');setMessage('');
    try {await task(revision)} catch(e){if(revision===generation.current)setError(e.message)} finally{if(revision===generation.current)setBusy(false)}
  }
  function open(id){act(async revision=>{const a=await api('/'+id);if(revision!==generation.current)return;setAnalysis(a);setCurrency(a.metrics[0]?.currency??'USD');setPendingDelete(null)})}
  const metrics=analysis?.metrics.filter(m=>m.currency===currency)??[], totals=summary(metrics);
  return <section className="page private-workspace"><div className="title-row"><div><p className="eyebrow">YOUR WORKSPACE</p><h1>Private uploads</h1><p className="muted">Save payment CSVs and inspect their metrics. Only your account can access them.</p></div>
    {session&&<button className="button" onClick={()=>act(async()=>{const {error}=await client.auth.signOut();if(error)throw error})}>Sign out</button>}</div>
    {error&&<p className="notice" role="alert">{error}</p>}{message&&<p className="notice" role="status">{message}</p>}
    {!client&&!error&&<p role="status">Connecting to accounts…</p>}
    {client&&(!session||mode==='newpassword')&&<form className="panel account-form" onSubmit={authenticate}>
      <div className="login-story"><span className="login-kicker">SENTINELPAY / YOUR WORKSPACE</span><h2>Find the signal.<br/><em>Follow the evidence.</em></h2><p>A quieter place to investigate payment failures. Bring a CSV, inspect the timeline, and ask better questions.</p><div className="signal-art" aria-hidden="true"><span>PAYMENT SIGNAL / ILLUSTRATION</span><svg viewBox="0 0 500 130"><path className="signal-grid" d="M0 30H500M0 65H500M0 100H500"/><path className="signal-line" d="M0 100L70 100L85 85L105 105L130 98L180 98L200 65L220 95L255 95L275 20L295 110L320 60L350 95L410 95L430 85L450 95L500 95"/></svg></div><ol className="login-steps"><li><b>01</b><span>Bring your data<small>Private CSV uploads</small></span></li><li><b>02</b><span>See what changed<small>Metrics and threshold checks</small></span></li><li><b>03</b><span>Investigate with context<small>Optional AI explanations</small></span></li></ol></div>
      <div className="login-controls"><span className="login-kicker">START AN INVESTIGATION</span><h2>{mode==='signup'?'Create your account':mode==='reset'?'Reset password':mode==='newpassword'?'Choose a new password':'Welcome to your workspace.'}</h2><p className="muted">Your files. Your analysis. One place to pick up where you left off.</p>
      {mode==='signin'&&<><button type="button" className="button google-signin" disabled={busy||!config.googleAuthReady} onClick={googleSignIn}>{busy?'Connecting…':'Continue with Google'}</button>
      <p className="muted">{config.googleAuthReady?'New here? Google sign-in creates your private account.':'Google sign-in is being connected.'}</p><button type="button" className="text-button" aria-expanded={emailExpanded} onClick={()=>setEmailExpanded(!emailExpanded)}>{emailExpanded?'Hide email sign-in':'Use an existing email account →'}</button></>}
      {(emailExpanded||mode!=='signin')&&<>{mode!=='newpassword'&&<label>Email<input type="email" autoComplete="email" required value={email} onChange={e=>setEmail(e.target.value)}/></label>}
      {mode!=='reset'&&<label>Password<input type="password" autoComplete={mode==='signin'?'current-password':'new-password'} minLength={mode==='signin'?1:12} maxLength={128} required value={password} onChange={e=>setPassword(e.target.value)}/></label>}
      {['signup','newpassword'].includes(mode)&&<p className="muted">Use at least 12 characters.</p>}
      <button className="button primary" disabled={busy}>{busy?'Please wait…':mode==='signup'?'Create account':mode==='reset'?'Send reset link':mode==='newpassword'?'Save password':'Sign in'}</button>
      {!config.emailAuthReady&&<p className="muted">Email/password registration and password-reset emails are not available yet.</p>}
      </>}<div className="account-links">{(config.emailAuthReady?['signin','signup','reset']:['signin']).filter(m=>m!==mode).map(m=><button type="button" className="text-button" key={m} onClick={()=>{setMode(m);setPassword('');setError('');setMessage('')}}>{m==='signin'?'Sign in':m==='signup'?'Create account':'Forgot password?'}</button>)}</div><p className="login-footnote">Use synthetic or de-identified data. <a href="/privacy.html">Privacy</a> · <a href="/terms.html">Terms</a></p></div>
    </form>}
    {session&&mode!=='newpassword'&&<><p className="muted">Signed in as {session.user.email}</p><form className="panel upload-form" onSubmit={e=>{e.preventDefault();if(!file)return;act(async revision=>{
      if(file.size>2*1024*1024)throw new Error('Choose a CSV of 2 MiB or less.');
      const body=new FormData();body.append('file',file);const saved=await api('',{method:'POST',body});
      const [list,a]=await Promise.all([api(),api('/'+saved.id)]);if(revision!==generation.current)return;
      setFiles(list);setFilesState('ready');setAnalysis(a);setCurrency(a.metrics[0]?.currency??'USD');setFile(null);fileInput.current.value='';setMessage('Upload saved.');
    })}}><div className="upload-intro"><div><p className="eyebrow">01 / IMPORT</p><h2>Bring your payment data.</h2><p>UTF-8 CSV · up to 5,000 rows · 2 MiB · 20 saved files</p></div><button type="button" className="button" onClick={downloadSample}>↓ Download sample CSV</button></div><p className="muted">Do not include card numbers, names, or email addresses. AI receives a summary only when you explicitly ask and consent.</p>
      <label>CSV file<input ref={fileInput} type="file" accept=".csv,text/csv" required onChange={e=>setFile(e.target.files[0]??null)}/></label>
      <button className="button primary" disabled={busy||filesState==='loading'||!file}>{busy?'Working…':'Upload and analyze'}</button>
      <details><summary>Required CSV format</summary><pre>id,timestamp,amount,currency,status,latencyMs{'\n'}pay-1,2026-01-01T12:00:00Z,19.99,USD,SUCCESS,120</pre><p>Use SUCCESS or FAILED. IDs must be unique within the file. At least 20 payments per minute are needed for failure and latency rules.</p></details>
    </form><section className="panel" aria-busy={filesState==='loading'}><div className="panel-heading"><h2>Saved files{filesState==='ready'?` (${files.length}/20)`:''}</h2><button className="button" disabled={busy||filesState==='loading'} onClick={()=>act(async rev=>{
      setFilesState('loading');
      try {const f=await api();if(rev===generation.current){setFiles(f);setFilesState('ready')}}
      catch(e){if(rev===generation.current)setFilesState('error');throw e}
    })}>Refresh files</button></div>
      {files.length>0&&<label className="file-search">Find a saved file<input type="search" placeholder="Search filenames…" value={fileSearch} onChange={e=>setFileSearch(e.target.value)}/></label>}
      {filesState==='loading'?<p className="loading-state" role="status">Loading saved files…</p>:filesState==='error'?<p>Could not load saved files. Use Refresh files to try again.</p>:!files.length?<div className="file-empty"><h3>Your first investigation starts here.</h3><p>Upload a payment CSV, or download the sample above to explore an eight-minute scenario.</p></div>:!files.some(f=>f.filename.toLowerCase().includes(fileSearch.toLowerCase()))?<p role="status">No filenames match your search.</p>:<ul className="upload-list">{files.filter(f=>f.filename.toLowerCase().includes(fileSearch.toLowerCase())).map(f=><li className={analysis?.upload.id===f.id?'selected-file':''} key={f.id}><button className="text-button" disabled={busy} onClick={()=>open(f.id)}>{f.filename}</button><span>{f.transactionCount} payments · {new Date(f.createdAt).toLocaleDateString()}</span><button className="text-button" disabled={busy} onClick={()=>setPendingDelete(f)}>Delete</button></li>)}</ul>}
      {pendingDelete&&<div className="notice"><p>Permanently delete {pendingDelete.filename} and all its transactions?</p><button className="button" disabled={busy} onClick={()=>act(async rev=>{await api('/'+pendingDelete.id,{method:'DELETE'});if(rev!==generation.current)return;setFiles(files.filter(f=>f.id!==pendingDelete.id));if(analysis?.upload.id===pendingDelete.id)setAnalysis(null);setPendingDelete(null);setMessage('File deleted.');})}>Delete permanently</button> <button className="button" onClick={()=>setPendingDelete(null)}>Cancel</button></div>}
    </section>{analysis&&<section className="panel"><div className="panel-heading"><h2>{analysis.upload.filename}</h2><label>Currency <select value={currency} onChange={e=>setCurrency(e.target.value)}>{[...new Set(analysis.metrics.map(m=>m.currency))].map(c=><option key={c}>{c}</option>)}</select></label></div>
      <div className="stats">{[['Payments',totals.count],['Failure rate',totals.failureRate==null?'—':totals.failureRate.toFixed(1)+'%'],['Average latency',totals.latency==null?'—':totals.latency.toFixed(0)+' ms']].map(([k,v])=><article className="stat" key={k}><span>{k}</span><strong>{v}</strong></article>)}</div><Chart metrics={metrics} signal="latency"/>
      <h3>Checks by minute · UTC</h3><p className="muted">Rules describe this file only. Missing minutes are not treated as zero traffic. These are threshold checks, not AI conclusions.</p><div className="upload-checks">{analysis.findings.filter(f=>f.currency===currency).map(f=><div key={f.bucket}><strong>{new Date(f.bucket).toISOString().replace('T',' ').slice(0,16)}</strong>{f.evaluations.map(e=><p key={e.rule}><span className={`severity ${e.state==='CRITICAL'?'critical':e.state==='WARNING'?'warning':'neutral'}`}>{e.state.replaceAll('_',' ')}</span> {e.rule.replaceAll('_',' ')} — {e.explanation}</p>)}</div>)}</div>
      <UploadAi key={analysis.upload.id+currency} id={analysis.upload.id} currency={currency} getToken={async()=>{
        const {data:{session:current}}=await client.auth.getSession();
        if(!current)throw new Error('Sign in again to continue.');return current.access_token;
      }}/>
    </section>}</>}
  </section>;
}
