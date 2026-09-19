export async function uploadRequest(base,token,path='',options={}) {
  const method=(options.method??'GET').toUpperCase();
  const controller=new AbortController();
  const cancel=()=>controller.abort(options.signal.reason);
  if(options.signal?.aborted)cancel();
  else options.signal?.addEventListener('abort',cancel,{once:true});
  let timedOut=false;
  const timer=setTimeout(()=>{timedOut=true;controller.abort();},90000);
  const uncertain=method==='POST'?' The upload may already have been saved. Refresh saved files before uploading it again.':
    method==='DELETE'?' The deletion may already have completed. Refresh saved files to check.':'';
  try {
  const response=await fetch(base+'/api/uploads'+path,{...options,headers:{Authorization:'Bearer '+token},cache:'no-store',signal:controller.signal});
  if(response.status===401)throw new Error('Your session expired. Please sign in again.');
  if(response.status===404)throw new Error('This file is unavailable or belongs to another account.');
  if(response.status===413)throw new Error('Choose a CSV of 2 MiB or less.');
  if(response.status===429)throw new Error('Request limit reached. Please try later. Uploads allow 5 attempts per minute and 50 per day.');
  if(response.status>=500)throw new Error('The upload service is temporarily unavailable. It may be waking up. Refresh saved files shortly.'+uncertain);
  if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.detail??'The upload service is unavailable. It may be waking up; try again shortly.');}
  return response.status===204?null:await response.json();
  } catch(error) {
    if(options.signal?.aborted)throw options.signal.reason??new DOMException('Request cancelled','AbortError');
    if(timedOut)throw new Error('The upload service took too long to respond. It may be waking up. Please try again shortly.'+uncertain);
    if(error instanceof TypeError)throw new Error('Could not reach the upload service. Check your connection and try again.'+uncertain);
    if(error instanceof SyntaxError)throw new Error('The upload service returned an unreadable response. Refresh saved files.'+uncertain);
    throw error;
  } finally {
    clearTimeout(timer);
    options.signal?.removeEventListener('abort',cancel);
  }
}
