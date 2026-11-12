import {describe,it,expect} from 'vitest';
import {groundEvidence,resolveReferences} from './evidence-grounding';
const row=(minute,count=20,failed=0,latency=100)=>({bucket:`2026-01-01T12:${String(minute).padStart(2,'0')}:00Z`,totalCount:count,failedCount:failed,averageLatencyMs:latency});
const facts=observations=>({currency:'USD',observations});
describe('server-calculated AI evidence',()=>{
  it('distinguishes equality at a lower volume threshold from falling below it',()=>{
    const g=groundEvidence({...facts([row(0)]),evidence:[{rule:'VOLUME_DROP',observed:20,criticalThreshold:20,warningThreshold:50,state:'CRITICAL'}]});
    expect(g.ruleChecks[0].criticalRelation).toBe('EQUAL_TO');expect(g.ruleChecks[0].breachDirection).toBe('AT_OR_BELOW');
  });
  it('calculates weighted summaries rather than averaging minute percentages',()=>{
    const g=groundEvidence(facts([row(0,20,10,100),row(1,80,0,500)]));
    expect(g.summary.failureRatePct).toBe(10);expect(g.summary.averageLatencyMs).toBe(420);
  });
  it('keeps gaps unknown and sparse samples explicit',()=>{
    const g=groundEvidence(facts([row(0,0),row(4,19)]));
    expect(g.quality.missingMinutes).toBe(3);expect(g.references[0].failureRatePct).toBeNull();expect(g.quality.undersampledMinutes).toBe(2);
  });
  it('excludes incident and future minutes from the baseline',()=>{
    const g=groundEvidence({...facts([row(0),row(1),row(2),row(3,20,10,2000),row(4,100,100,9000)]),bucket:row(3).bucket});
    expect(g.comparison.baselineMinutes).toHaveLength(3);expect(g.comparison.failureRateChangePp).toBe(50);expect(g.comparison.latencyChangeMs).toBe(1900);
  });
  it('rejects invalid counts, latencies, duplicate minutes, and unknown currency',()=>{
    for(const observations of [[row(0,20,21)],[row(0,-1)],[row(0,20,0,Infinity)],[row(0),row(0)]])expect(()=>groundEvidence(facts(observations))).toThrow();
    expect(()=>groundEvidence({...facts([row(0)]),currency:'MIXED'})).toThrow();
  });
  it('does not compare undersampled selected minutes',()=>{expect(groundEvidence(facts([row(0),row(1),row(2),row(3,19)])).comparison.state).toBe('INSUFFICIENT_DATA')});
  it('rejects fabricated references and returns server-owned values',()=>{
    const g=groundEvidence(facts([row(0)]));expect(()=>resolveReferences(['M99'],g)).toThrow();expect(()=>resolveReferences([],g)).toThrow();
    expect(resolveReferences(['M1','M1'],g)).toEqual([g.references[0]]);
  });
});
