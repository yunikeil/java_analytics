package dev.local.analytics.model;

import java.util.UUID;

public record Trainer(UUID id, String title, String description, String difficulty, int taskCount) {
}

