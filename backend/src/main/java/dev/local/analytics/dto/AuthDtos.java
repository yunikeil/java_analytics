package dev.local.analytics.dto;

import dev.local.analytics.model.User;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(String email, String password, String fullName) {
    }

    public record ConfirmRegistrationRequest(String email, String otp) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record AuthResponse(String token, User user) {
    }
}

