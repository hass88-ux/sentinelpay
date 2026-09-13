import React from 'react';
import {segments} from './data';
export const clock = value => new Date(value).toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit',timeZone:'UTC'});
export const number = (value, digits=0) => value==null?'—':Number(value).toLocaleString('en-US',{maximumFractionDigits:digits});
export default function Chart({metrics,signal}) {
  if(!metrics.length) return <div className="empty">No observed minutes for this currency.</div>;
  const value=m=>signal==='latency'?m.averageLatencyMs:signal==='failure'?m.failedCount/m.totalCount*100:m.totalCount;
  const warning=signal==='latency'?1000:signal==='failure'?20:null;
  const max=Math.max(...metrics.map(value),warning??1)*1.2 || 1;
  const minTime=+new Date(metrics[0].bucket),maxTime=+new Date(metrics.at(-1).bucket);
  const x=m=>maxTime===minTime?340:52+(+new Date(m.bucket)-minTime)/(maxTime-minTime)*568;
  const y=m=>210-value(m)/max*175;
  const ticks=metrics.filter((m,i)=>i%Math.ceil(metrics.length/6)===0 || i===metrics.length-1);
  return <><div className="chart"><svg viewBox="0 0 660 250" role="img" aria-label={`${signal==='latency'?'Average latency in milliseconds':signal==='failure'?'Failure percentage':'Payment volume'} across ${metrics.length} observed UTC minutes`}>
    <defs><linearGradient id="area" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#f4c35d" stopOpacity=".18"/><stop offset="1" stopColor="#f4c35d" stopOpacity="0"/></linearGradient></defs>
    {[0,.25,.5,.75,1].map(f=><g key={f}><line x1="52" x2="632" y1={210-f*175} y2={210-f*175} stroke="#30373d"/><text x="0" y={214-f*175}>{number(max*f)}</text></g>)}
    {warning!==null&&<line x1="52" x2="632" y1={210-warning/max*175} y2={210-warning/max*175} stroke="#b89a59" strokeDasharray="5 5"/>}
    {segments(metrics).map((part,i)=><g key={i}><path d={`M ${x(part[0])} 210 ${part.map(m=>`L ${x(m)} ${y(m)}`).join(' ')} L ${x(part.at(-1))} 210 Z`} fill="url(#area)"/><polyline points={part.map(m=>`${x(m)},${y(m)}`).join(' ')} fill="none" stroke="#f4c35d" strokeWidth="3" strokeLinejoin="round"/>
    {part.map(m=><circle key={m.bucket} cx={x(m)} cy={y(m)} r="4" fill="#f4c35d"><title>{clock(m.bucket)} UTC: {number(value(m),1)}</title></circle>)}</g>)}
    {ticks.map(m=><text key={m.bucket} x={x(m)} y="240" textAnchor="middle">{clock(m.bucket)}</text>)}
  </svg></div><div className="chart-foot"><span>{warning!==null?<><i className="warning-line"/>Warning · {number(warning)}{signal==='failure'?'%':' ms'}</>:'Payment count per observed minute'}</span><span>UTC · gaps stay empty</span></div>
  <details className="data-details"><summary>View chart data</summary><div className="table-scroll"><table><thead><tr><th>Minute (UTC)</th><th>Payments</th><th>Failure rate</th><th>Average latency</th></tr></thead><tbody>{metrics.map(m=><tr key={m.bucket}><td>{clock(m.bucket)}</td><td>{number(m.totalCount)}</td><td>{number(m.failedCount/m.totalCount*100,1)}%</td><td>{number(m.averageLatencyMs,1)} ms</td></tr>)}</tbody></table></div></details></>;
}
