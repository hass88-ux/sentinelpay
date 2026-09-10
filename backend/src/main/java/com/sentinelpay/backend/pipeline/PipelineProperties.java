package com.sentinelpay.backend.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Validated
@ConfigurationProperties("pipeline")
public record PipelineProperties(@NotBlank String bootstrapServers, @NotBlank String jdbcUrl,
        @NotBlank String username, String password,
        @Pattern(regexp = "[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,200}") @NotNull String topic,
        @Pattern(regexp = "[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,200}") @NotNull String deadLetterTopic,
        @NotBlank String groupId, @Min(1) @Max(12) int partitions) {
    public PipelineProperties {
        if (topic != null && topic.equals(deadLetterTopic)) {
            throw new IllegalArgumentException("payment and dead-letter topics must differ");
        }
    }
}
