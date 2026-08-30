package com.rayflow.server.metadata.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record MetadataEventEnvelope(
        String eventId,
        int version,
        String eventType,
        String source,
        Instant occurredAt,
        String correlationId,
        String assetKey,
        JsonNode payload
) {
    public static final int CURRENT_VERSION = 1;

    public MetadataEventEnvelope {
        if (version < 1) {
            throw new IllegalArgumentException("event envelope version must be positive");
        }
    }
}
