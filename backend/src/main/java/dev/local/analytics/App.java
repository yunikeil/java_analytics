package dev.local.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.local.analytics.config.AppConfig;
import dev.local.analytics.controller.AuthController;
import dev.local.analytics.controller.ProgressController;
import dev.local.analytics.controller.TaskController;
import dev.local.analytics.controller.TrainerController;
import dev.local.analytics.db.Database;
import dev.local.analytics.metrics.MetricsRegistry;
import dev.local.analytics.repository.AttemptRepository;
import dev.local.analytics.repository.ProgressRepository;
import dev.local.analytics.repository.TaskRepository;
import dev.local.analytics.repository.TrainerRepository;
import dev.local.analytics.repository.UserRepository;
import dev.local.analytics.service.AuthService;
import dev.local.analytics.service.MailService;
import dev.local.analytics.service.ScoringService;
import dev.local.analytics.service.TaskService;
import dev.local.analytics.telemetry.Telemetry;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;
import java.util.Map;
import java.util.UUID;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:8081",
            "http://127.0.0.1:8081"
    );

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnv();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Telemetry telemetry = Telemetry.create(config);
        Database database = Database.create(config);
        database.migrate();

        JedisPool redis = new JedisPool(new JedisPoolConfig(), config.redisHost(), config.redisPort());
        UserRepository userRepository = new UserRepository(database.dataSource());
        TrainerRepository trainerRepository = new TrainerRepository(database.dataSource(), mapper);
        TaskRepository taskRepository = new TaskRepository(database.dataSource(), mapper);
        AttemptRepository attemptRepository = new AttemptRepository(database.dataSource(), mapper);
        ProgressRepository progressRepository = new ProgressRepository(database.dataSource());
        MetricsRegistry metrics = new MetricsRegistry();

        MailService mailService = new MailService(config);
        AuthService authService = new AuthService(config, mapper, redis, userRepository, mailService);
        ScoringService scoringService = new ScoringService(mapper);
        TaskService taskService = new TaskService(taskRepository, attemptRepository, progressRepository, scoringService);

        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
            });
        });

        app.before(ctx -> {
            String origin = ctx.header("Origin");
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                ctx.header("Access-Control-Allow-Origin", origin);
                ctx.header("Access-Control-Allow-Credentials", "true");
                ctx.header("Vary", "Origin");
            }
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        });
        app.options("/*", ctx -> ctx.status(204));

        app.before(ctx -> {
            metrics.markStart(ctx);
            telemetry.startHttpSpan(ctx);
        });
        app.after(ctx -> {
            metrics.record(ctx);
            telemetry.finishHttpSpan(ctx);
        });

        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (String.valueOf(ctx.method()).equalsIgnoreCase("OPTIONS") || path.startsWith("/api/auth/")) {
                return;
            }
            String header = ctx.header("Authorization");
            String token = header != null && header.startsWith("Bearer ")
                    ? header.substring("Bearer ".length())
                    : ctx.cookie("auth_token");
            if (token == null || token.isBlank()) {
                throw new SecurityException("Missing bearer token");
            }
            UUID userId = authService.verify(token);
            ctx.attribute("userId", userId);
        });

        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            log.warn("Bad request: {}", e.getMessage());
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        });
        app.exception(SecurityException.class, (e, ctx) -> {
            log.warn("Unauthorized: {}", e.getMessage());
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", e.getMessage()));
        });
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled API error", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Internal server error"));
        });

        app.get("/health", ctx -> ctx.json(Map.of("status", "ok")));
        app.get("/metrics", ctx -> ctx.contentType("text/plain; version=0.0.4").result(metrics.render()));
        new AuthController(mapper, authService).routes(app);
        new TrainerController(trainerRepository, taskRepository).routes(app);
        new TaskController(mapper, taskRepository, taskService).routes(app);
        new ProgressController(progressRepository, attemptRepository).routes(app);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            redis.close();
            database.close();
            telemetry.close();
        }));

        app.start(config.port());
    }
}
