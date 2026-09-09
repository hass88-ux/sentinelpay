package com.sentinelpay.backend.pipeline;

import java.math.BigDecimal;
import java.time.Instant;

public record MinuteMetric(Instant bucket, String currency, long totalCount, long successCount,
        long failedCount, BigDecimal totalAmount, BigDecimal averageLatencyMs, long maxLatencyMs) {
}
