package dev.local.analytics.controller;

import dev.local.analytics.repository.AttemptRepository;
import dev.local.analytics.repository.ProgressRepository;
import io.javalin.Javalin;

import java.util.UUID;

public class ProgressController {
    private final ProgressRepository progress;
    private final AttemptRepository attempts;

    public ProgressController(ProgressRepository progress, AttemptRepository attempts) {
        this.progress = progress;
        this.attempts = attempts;
    }

    public void routes(Javalin app) {
        app.get("/api/progress", ctx -> ctx.json(progress.findByUser((UUID) ctx.attribute("userId"))));
        app.get("/api/attempts", ctx -> ctx.json(attempts.findByUser((UUID) ctx.attribute("userId"))));
    }
}
