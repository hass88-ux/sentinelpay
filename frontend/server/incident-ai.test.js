import { describe, expect, it, vi } from 'vitest';
import snapshot from '../public/demo/snapshot.json';
import { answerIncident, createHandler, createLimiter, factsFor, readJsonLimited, validateAnswer } from './incident-ai.js';

const id = snapshot.cases[0].id;
const answer = { answer: 'Latency rose to 2,500 ms. The cause remains unknown.', evidenceRules: ['AVERAGE_LATENCY'], nextChecks: ['Check provider latency logs.'] };
const providerReply = () => new Response(JSON.stringify({ choices: [{ finish_reason: 'stop', message: { content: JSON.stringify(answer) } }] }));
const url = `https://example.com/api/demo/incidents/${id}/questions`;
const questionRequest = (body = { question: 'Why was this flagged?' }, origin = 'https://example.com') => new Request(url, {
  method: 'POST', headers: { 'Content-Type': 'application/json', Origin: origin, 'CF-Connecting-IP': 'test-visitor' }, body: JSON.stringify(body),
});

describe('case evidence boundaries', () => {
  it('sends only the selected aggregate evidence and chronological observations', () => {
    const facts = factsFor(snapshot, id);
    expect(facts.observations).toHaveLength(6);
    expect(facts.observations.at(-1).averageLatencyMs).toBe(2500);
    expect(facts.observations[0].averageLatencyMs).toBe(200);
    expect(JSON.stringify(facts)).not.toContain(snapshot.transactions[0].id);
    expect(JSON.stringify(facts)).not.toContain('totalAmount');
    expect(() => factsFor(snapshot, 'toString')).toThrow('not in the demo');
  });
  it('rejects citations that are not part of the case', () => {
    expect(() => validateAnswer({ ...answer, evidenceRules: ['INVENTED_RULE'] }, ['AVERAGE_LATENCY'])).toThrow('not in this incident');
    expect(() => validateAnswer({ ...answer, evidenceRules: [] }, ['AVERAGE_LATENCY'])).toThrow();
  });
  it('rejects oversized or incomplete answers', () => {
    expect(() => validateAnswer({ ...answer, answer: 'a'.repeat(2001) }, ['AVERAGE_LATENCY'])).toThrow();
    expect(() => validateAnswer({ ...answer, nextChecks: [null] }, ['AVERAGE_LATENCY'])).toThrow();
  });
});

describe('provider boundary', () => {
  it('returns validated AI output and keeps the key out of the answer', async () => {
    const fetcher = vi.fn(async () => providerReply());
    const result = await answerIncident({ snapshot, id, question: 'Why?', key: 'private-test-key', fetcher });
    expect(result.mode).toBe('AI_ASSISTED');
    expect(result.answer).toBe(answer.answer);
    expect(JSON.stringify(result)).not.toContain('private-test-key');
    const payload = JSON.parse(fetcher.mock.calls[0][1].body);
    expect(payload.messages[1].role).toBe('user');
    expect(payload.response_format.json_schema.strict).toBe(true);
  });
  it('does not call the provider for a missing key or invalid question', async () => {
    const fetcher = vi.fn();
    await expect(answerIncident({ snapshot, id, question: 'Why?', fetcher })).rejects.toThrow('not configured');
    await expect(answerIncident({ snapshot, id, question: 'x'.repeat(501), key: 'key', fetcher })).rejects.toThrow('1 to 500');
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('does not expose provider error details', async () => {
    await expect(answerIncident({ snapshot, id, question: 'Why?', key: 'key',
      fetcher: async () => new Response('secret diagnostic', { status: 401 }) })).rejects.toThrow('provider is unavailable');
  });
  it('handles quota exhaustion without automatic retries', async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 429 }));
    await expect(answerIncident({ snapshot, id, question: 'Why?', key: 'key', fetcher })).rejects.toMatchObject({ status: 429 });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('rejects truncated output', async () => {
    await expect(answerIncident({ snapshot, id, question: 'Why?', key: 'key', fetcher: async () =>
      new Response(JSON.stringify({ choices: [{ finish_reason: 'length', message: { content: JSON.stringify(answer) } }] }))
    })).rejects.toThrow('did not finish');
  });
  it('aborts slow model calls', async () => {
    const fetcher = (_url, options) => new Promise((_resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(new Error('aborted')));
    });
    await expect(answerIncident({ snapshot, id, question: 'Why?', key: 'key', fetcher, timeout: 5 })).rejects.toMatchObject({ status: 504 });
  });
  it('caps input even without Content-Length', async () => {
    await expect(readJsonLimited(new Response('x'.repeat(4097)).body, 4096)).rejects.toMatchObject({ status: 413 });
  });
});

describe('public endpoint', () => {
  it('rejects foreign origins and browser-supplied evidence', async () => {
    const fetcher = vi.fn(); const handler = createHandler(snapshot, { fetcher });
    expect((await handler(questionRequest(undefined, 'https://other.example'), { GROQ_API_KEY: 'key' })).status).toBe(403);
    expect((await handler(questionRequest({ question: 'Why?', facts: 'fake' }), { GROQ_API_KEY: 'key' })).status).toBe(400);
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('limits repeated requests before calling the provider', async () => {
    const fetcher = vi.fn(async () => providerReply());
    const handler = createHandler(snapshot, { fetcher });
    for (let n = 0; n < 3; n++) expect((await handler(questionRequest(), { GROQ_API_KEY: 'key' })).status).toBe(200);
    expect((await handler(questionRequest(), { GROQ_API_KEY: 'key' })).status).toBe(429);
    expect(fetcher).toHaveBeenCalledTimes(3);
  });
  it('resets the limiter after the minute and caps all visitors', () => {
    let now = 0; const limit = createLimiter(() => now);
    for (let n = 0; n < 12; n++) expect(limit(`visitor-${n}`)).toBe(true);
    expect(limit('new-visitor')).toBe(false);
    now = 60000; expect(limit('new-visitor')).toBe(true);
  });
  it('status reveals configuration but never the key', async () => {
    const response = await createHandler(snapshot)(new Request('https://example.com/api/demo/ai/status'), { GROQ_API_KEY: 'hidden-key' });
    const body = await response.json(); expect(body.enabled).toBe(true);
    expect(JSON.stringify(body)).not.toContain('hidden-key');
  });
});
