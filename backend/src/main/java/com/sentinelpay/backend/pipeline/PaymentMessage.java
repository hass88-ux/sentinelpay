package com.sentinelpay.backend.pipeline;

import com.sentinelpay.backend.transaction.TransactionEvent;

/** Explicit wire version so future incompatible changes cannot be silently consumed. */
public record PaymentMessage(int schemaVersion, TransactionEvent event) {
    public PaymentMessage {
        if (schemaVersion != 1 || event == null) {
            throw new IllegalArgumentException("a version 1 payment event is required");
        }
    }
}
