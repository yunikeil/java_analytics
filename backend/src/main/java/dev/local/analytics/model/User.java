package dev.local.analytics.model;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String email, String fullName, String role, Instant createdAt) {
}

