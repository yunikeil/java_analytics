package dev.local.analytics.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record Attempt(
        UUID id,
        UUID userId,
        UUID taskId,
        JsonNode submittedAnswer,
        double score,
        int maxScore,
        boolean correct,
        String feedback,
        Instant createdAt
) {
}

