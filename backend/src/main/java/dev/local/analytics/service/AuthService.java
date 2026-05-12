package dev.local.analytics.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.analytics.config.AppConfig;
import dev.local.analytics.dto.AuthDtos.AuthResponse;
import dev.local.analytics.dto.AuthDtos.RegisterRequest;
import dev.local.analytics.model.User;
import dev.local.analytics.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AuthService {
    private final AppConfig config;
    private final ObjectMapper mapper;
    private final JedisPool redis;
    private final UserRepository users;
    private final MailService mail;
    private final Algorithm algorithm;
    private final Random random = new Random();

    public AuthService(AppConfig config, ObjectMapper mapper, JedisPool redis, UserRepository users, MailService mail) {
        this.config = config;
        this.mapper = mapper;
        this.redis = redis;
        this.users = users;
        this.mail = mail;
        this.algorithm = Algorithm.HMAC256(config.jwtSecret());
    }

    public void requestRegistration(RegisterRequest request) {
        validateEmail(request.email());
        if (request.password() == null || request.password().length() < 6) {
            throw new IllegalArgumentException("Password must contain at least 6 characters");
        }
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        users.findByEmailWithPassword(request.email()).ifPresent(user -> {
            throw new IllegalArgumentException("User already exists");
        });

        String otp = String.format("%06d", random.nextInt(1_000_000));
        String payload;
        try {
            payload = mapper.writeValueAsString(Map.of(
                    "email", request.email().toLowerCase(),
                    "fullName", request.fullName(),
                    "passwordHash", BCrypt.hashpw(request.password(), BCrypt.gensalt()),
                    "otpHash", BCrypt.hashpw(otp, BCrypt.gensalt())
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot prepare registration", e);
        }

        try (var jedis = redis.getResource()) {
            jedis.setex(pendingKey(request.email()), 600, payload);
        }
        mail.sendOtp(request.email(), otp);
    }

    @SuppressWarnings("unchecked")
    public AuthResponse confirmRegistration(String email, String otp) {
        validateEmail(email);
        if (otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("OTP is required");
        }
        String payload;
        try (var jedis = redis.getResource()) {
            payload = jedis.get(pendingKey(email));
        }
        if (payload == null) {
            throw new IllegalArgumentException("OTP expired or registration was not requested");
        }
        try {
            Map<String, String> pending = mapper.readValue(payload, Map.class);
            if (!BCrypt.checkpw(otp, pending.get("otpHash"))) {
                throw new IllegalArgumentException("Invalid OTP");
            }
            User user = users.create(pending.get("email"), pending.get("passwordHash"), pending.get("fullName"));
            try (var jedis = redis.getResource()) {
                jedis.del(pendingKey(email));
            }
            return new AuthResponse(issueToken(user), user);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot confirm registration", e);
        }
    }

    public AuthResponse login(String email, String password) {
        validateEmail(email);
        UserRepository.UserWithPassword found = users.findByEmailWithPassword(email)
                .orElseThrow(() -> new SecurityException("Invalid email or password"));
        if (password == null || !BCrypt.checkpw(password, found.passwordHash())) {
            throw new SecurityException("Invalid email or password");
        }
        return new AuthResponse(issueToken(found.user()), found.user());
    }

    public UUID verify(String token) {
        try {
            String subject = JWT.require(algorithm).withIssuer(config.serviceName()).build().verify(token).getSubject();
            return UUID.fromString(subject);
        } catch (JWTVerificationException | IllegalArgumentException e) {
            throw new SecurityException("Invalid token");
        }
    }

    public User me(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new SecurityException("User not found"));
    }

    private String issueToken(User user) {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        return JWT.create()
                .withIssuer(config.serviceName())
                .withSubject(user.id().toString())
                .withClaim("email", user.email())
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
    }

    private void validateEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
    }

    private String pendingKey(String email) {
        return "otp:registration:" + email.toLowerCase();
    }
}

