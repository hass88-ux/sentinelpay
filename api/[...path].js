import {readFileSync} from 'node:fs';
import {createVercelHandler} from '../frontend/server/vercel-handler.js';

const snapshot=JSON.parse(readFileSync(new URL('../frontend/public/demo/snapshot.json',import.meta.url),'utf8'));
const handle=createVercelHandler(snapshot);
export default async function handler(req,res) {
  try {
    const headers=new Headers();
    for(const [name,value] of Object.entries(req.headers))if(value!==undefined)headers.set(name,Array.isArray(value)?value.join(','):value);
    // Vercel supplies the deployment host. Never use a caller-provided URL as an upstream.
    const url=new URL(req.url,`https://${req.headers.host}`);
    headers.set('CF-Connecting-IP',req.headers['x-vercel-forwarded-for']??'unknown');
    let body;
    if(!['GET','HEAD'].includes(req.method)) {
      if(req.body!==undefined)body=Buffer.isBuffer(req.body)?req.body:typeof req.body==='string'?Buffer.from(req.body):Buffer.from(JSON.stringify(req.body));
      else {const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>2200000){res.statusCode=413;res.end('{"detail":"File is too large."}');return}chunks.push(chunk)}body=Buffer.concat(chunks)}
      if(body.length>2200000){res.statusCode=413;res.end('{"detail":"File is too large."}');return}
    }
    const response=await handle(new Request(url,{method:req.method,headers,body}),process.env);
    res.statusCode=response.status;
    response.headers.forEach((value,name)=>res.setHeader(name,value));
    res.end(Buffer.from(await response.arrayBuffer()));
  }catch{res.statusCode=500;res.setHeader('Cache-Control','no-store');res.end('{"detail":"The request could not be completed."}')}
}
