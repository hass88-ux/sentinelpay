// Fixed synthetic data: five baseline minutes, then a latency/failure spike.
export function sampleCsv() {
  const lines=['id,timestamp,amount,currency,status,latencyMs'];
  for(let minute=0;minute<8;minute++)for(let row=0;row<(minute===7?5:25);row++) {
    const failed=(minute===5&&row<8)||(minute===6&&row<15);
    lines.push(`sample-${minute}-${row},2026-01-01T12:0${minute}:00Z,19.99,USD,${failed?'FAILED':'SUCCESS'},${minute===5?1250:minute===6?2350:200}`);
  }
  return lines.join('\r\n')+'\r\n';
}
export function downloadSample() {
  const url=URL.createObjectURL(new Blob([sampleCsv()],{type:'text/csv;charset=utf-8'}));
  const link=document.createElement('a');link.href=url;link.download='sentinelpay-sample.csv';link.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
}
