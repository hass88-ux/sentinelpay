import { afterEach, describe, expect, it, vi } from 'vitest';
import { orderedMetrics, request, savedAnswer, segments, summary } from './data.js';

afterEach(() => vi.unstubAllGlobals());

describe('payment summaries', () => {
  it('weights latency and failure rate by transaction count', () => {
    const result = summary([
      { totalCount: 90, failedCount: 9, averageLatencyMs: 100 },
      { totalCount: 10, failedCount: 5, averageLatencyMs: 1000 },
    ]);
    expect(result.count).toBe(100);
    expect(result.failed).toBe(14);
    expect(result.latency).toBe(190);
    expect(result.failureRate).toBeCloseTo(14);
  });
  it('does not represent absent observations as healthy zeroes', () => {
    expect(summary([])).toEqual({ count: 0, failed: 0, failureRate: null, latency: null });
  });
  it('separates currencies and leaves gaps in the timeline', () => {
    const metrics = [
      { currency: 'USD', bucket: '2026-01-01T00:03:00Z' },
      { currency: 'EUR', bucket: '2026-01-01T00:02:00Z' },
      { currency: 'USD', bucket: '2026-01-01T00:00:00Z' },
      { currency: 'USD', bucket: '2026-01-01T00:01:00Z' },
    ];
    expect(segments(orderedMetrics(metrics, 'USD')).map(s => s.length)).toEqual([2, 1]);
    expect(metrics[0].bucket).toBe('2026-01-01T00:03:00Z');
  });
});

it('only replays the selected incident’s saved answer', () => {
  const recorded = { answer: 'Evidence from this case', mode: 'RULE_BASED' };
  const data = { questions: { first: { 'Why?': recorded } } };
  expect(savedAnswer(data, 'first', ' why? ')).toBe(recorded);
  expect(savedAnswer(data, 'second', 'Why?').mode).toBe('SAVED_DEMO');
  expect(savedAnswer(data, 'first', 'An unsaved question').mode).toBe('SAVED_DEMO');
});

it('surfaces API failures instead of displaying demo data', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503, json: async () => ({ detail: 'Pipeline unavailable' }) }));
  await expect(request('/api/pipeline/metrics')).rejects.toThrow('Pipeline unavailable');
});

it('reports an HTML response as a connection problem', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => { throw new SyntaxError(); } }));
  await expect(request('/api/pipeline/metrics')).rejects.toThrow('did not return JSON');
});

it('cancels a stalled request', async () => {
  vi.stubGlobal('fetch', vi.fn((_path, options) => new Promise((_resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
  })));
  await expect(request('/api/pipeline/metrics', { timeout: 5 })).rejects.toThrow('timed out');
});
