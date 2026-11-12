import {describe,it,expect} from 'vitest';
import {minuteState,investigate,investigationBrief} from './investigation';
const m=(minute,total=20,failed=0,latency=100,currency='USD')=>({bucket:`2026-01-01T12:${String(minute).padStart(2,'0')}:00Z`,currency,totalCount:total,failedCount:failed,averageLatencyMs:latency});
describe('investigation desk',()=>{
  it('never labels an undersampled minute healthy or critical',()=>{expect(minuteState(m(0,19,19,3000))).toBe('INSUFFICIENT_DATA')});
  it('uses inclusive warning and critical thresholds',()=>{expect(minuteState(m(0,20,4))).toBe('WARNING');expect(minuteState(m(0,20,10))).toBe('CRITICAL');expect(minuteState(m(0,20,0,1000))).toBe('WARNING');expect(minuteState(m(0,20,0,2000))).toBe('CRITICAL')});
  it('weights the baseline by count and excludes selected, future and other currencies',()=>{
    const rows=[m(0,20,2,100),m(1,40,4,400),m(2,20,2,100),m(3,20,10,2000),m(4,100,100,9000),m(1,100,100,9000,'EUR')];
    const r=investigate(rows,rows[3].bucket);expect(r.preceding).toHaveLength(3);expect(r.baseline.latency).toBe(250);expect(r.baseline.failureRate).toBe(10);
  });
  it('does not manufacture missing minutes or use stale baselines',()=>{expect(investigate([m(0),m(1),m(2),m(20)],m(20).bucket).baseline).toBeNull()});
  it('requires three qualifying preceding minutes',()=>{expect(investigate([m(0),m(1),m(2,19),m(3)],m(3).bucket).baseline).toBeNull()});
  it('exports evidence with limits, not a claimed AI conclusion',()=>{const text=investigationBrief([m(0,20,10)],m(0).bucket,'Private upload');expect(text).toContain('Private upload');expect(text).toContain('CRITICAL');expect(text).toContain('confirmed root cause');expect(text).not.toContain('undefined')});
  it('handles empty observations',()=>{expect(investigate([])).toBeNull()});
});
