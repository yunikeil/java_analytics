package dev.local.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.analytics.dto.AuthDtos.ConfirmRegistrationRequest;
import dev.local.analytics.dto.AuthDtos.LoginRequest;
import dev.local.analytics.dto.AuthDtos.RegisterRequest;
import dev.local.analytics.service.AuthService;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AuthController {
    private final ObjectMapper mapper;
    private final AuthService auth;

    public AuthController(ObjectMapper mapper, AuthService auth) {
        this.mapper = mapper;
        this.auth = auth;
    }

    public void routes(Javalin app) {
        app.post("/api/auth/register/request", ctx -> {
            RegisterRequest request = mapper.readValue(ctx.body(), RegisterRequest.class);
            auth.requestRegistration(request);
            ctx.status(HttpStatus.ACCEPTED).json(Map.of("status", "otp_sent"));
        });
        app.post("/api/auth/register/confirm", ctx -> {
            ConfirmRegistrationRequest request = mapper.readValue(ctx.body(), ConfirmRegistrationRequest.class);
            var response = auth.confirmRegistration(request.email(), request.otp());
            setAuthCookie(ctx, response.token());
            ctx.json(response);
        });
        app.post("/api/auth/login", ctx -> {
            LoginRequest request = mapper.readValue(ctx.body(), LoginRequest.class);
            var response = auth.login(request.email(), request.password());
            setAuthCookie(ctx, response.token());
            ctx.json(response);
        });
        app.get("/api/me", ctx -> ctx.json(auth.me((UUID) ctx.attribute("userId"))));
    }

    private void setAuthCookie(io.javalin.http.Context ctx, String token) {
        ctx.header("Set-Cookie", "auth_token=" + token + "; Max-Age=86400; Path=/; SameSite=Lax");
    }
}
