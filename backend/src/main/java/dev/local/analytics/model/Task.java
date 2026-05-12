package dev.local.analytics.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record Task(
        UUID id,
        UUID trainerId,
        TaskType type,
        String title,
        String prompt,
        int maxScore,
        JsonNode options,
        JsonNode correctAnswer,
        JsonNode artifact
) {
}

