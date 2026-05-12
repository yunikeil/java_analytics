package dev.local.analytics.dto;

import com.fasterxml.jackson.databind.JsonNode;

public final class TaskDtos {
    private TaskDtos() {
    }

    public record SubmitRequest(JsonNode answer) {
    }

    public record SubmitResponse(double score, int maxScore, boolean correct, String feedback) {
    }
}

