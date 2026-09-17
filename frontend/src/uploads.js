export async function uploadRequest(base,token,path='',options={}) {
  const response=await fetch(base+'/api/uploads'+path,{...options,headers:{Authorization:'Bearer '+token},cache:'no-store',signal:options.signal??AbortSignal.timeout(60000)});
  if(response.status===401)throw new Error('Your session expired. Please sign in again.');
  if(response.status===404)throw new Error('This file is unavailable or belongs to another account.');
  if(response.status===413)throw new Error('Choose a CSV of 2 MiB or less.');
  if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.detail??'The upload service is unavailable. It may be waking up; try again shortly.');}
  return response.status===204?null:response.json();
}
