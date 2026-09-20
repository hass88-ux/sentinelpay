import {expect,it} from 'vitest';
import {sampleCsv} from './sample-csv';
it('provides a valid deterministic sample with a baseline and degradation',()=>{
  const [header,...rows]=sampleCsv().trim().split('\r\n');
  expect(header).toBe('id,timestamp,amount,currency,status,latencyMs');
  expect(rows).toHaveLength(180);
  expect(new Set(rows.map(r=>r.split(',')[0])).size).toBe(180);
  expect(rows.filter(r=>r.includes(',FAILED,'))).toHaveLength(23);
  expect(rows.every(r=>!Number.isNaN(Date.parse(r.split(',')[1])))).toBe(true);
});
