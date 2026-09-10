package com.sentinelpay.backend.pipeline;

public class PublishUnavailableException extends RuntimeException {
    private final PublishReport report;

    public PublishUnavailableException(PublishReport report, Throwable cause) {
        super(report.detail(), cause);
        this.report = report;
    }

    public PublishReport report() {
        return report;
    }
}
