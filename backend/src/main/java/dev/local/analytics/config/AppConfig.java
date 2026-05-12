package dev.local.analytics.config;

public record AppConfig(
        int port,
        String jdbcUrl,
        String jdbcUser,
        String jdbcPassword,
        String redisHost,
        int redisPort,
        String jwtSecret,
        String smtpHost,
        int smtpPort,
        String mailFrom,
        String otlpEndpoint,
        String serviceName
) {
    public static AppConfig fromEnv() {
        return new AppConfig(
                intEnv("APP_PORT", 8080),
                env("JDBC_URL", "jdbc:postgresql://localhost:5432/analytics_trainer"),
                env("JDBC_USER", "trainer"),
                env("JDBC_PASSWORD", "trainer"),
                env("REDIS_HOST", "localhost"),
                intEnv("REDIS_PORT", 6379),
                env("JWT_SECRET", "dev-secret-change-me"),
                env("SMTP_HOST", "localhost"),
                intEnv("SMTP_PORT", 1025),
                env("MAIL_FROM", "trainer@local.dev"),
                env("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
                env("OTEL_SERVICE_NAME", "analytics-trainer-api")
        );
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intEnv(String key, int fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }
}

