// Temporary first-preview fixture. The published sample is exported by the Java demo.
export const sample = {
  capturedAt: '2026-09-13T12:08:00Z',
  metrics: [200,350,500,650,800,2500].map((latency,i) => ({bucket:`2026-09-13T12:0${i}:00Z`,currency:'USD',totalCount:i===5?20:100,failedCount:i===5?18:i*4,successCount:i===5?2:100-i*4,totalAmount:i===5?20:100,averageLatencyMs:latency,maxLatencyMs:latency})),
  cases: [{id:'preview',bucket:'2026-09-13T12:05:00Z',currency:'USD',state:'ALERT',severity:'CRITICAL'}],
  reports: {}, transactions: [], questions: {},
};
