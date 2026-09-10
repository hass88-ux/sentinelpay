package com.sentinelpay.backend.pipeline;

import java.util.List;

public record PublishReport(String state, List<String> acknowledgedIds, String uncertainId, String detail) {
    public PublishReport {
        acknowledgedIds = List.copyOf(acknowledgedIds);
    }
}
