package dev.local.analytics.model;

import java.time.Instant;
import java.util.UUID;

public record Progress(
        UUID userId,
        UUID trainerId,
        String trainerTitle,
        int completedTasks,
        int totalTasks,
        double totalScore,
        int maxScore,
        Instant updatedAt
) {
}

