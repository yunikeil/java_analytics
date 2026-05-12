package dev.local.analytics.controller;

import dev.local.analytics.repository.TaskRepository;
import dev.local.analytics.repository.TrainerRepository;
import io.javalin.Javalin;

import java.util.UUID;

public class TrainerController {
    private final TrainerRepository trainers;
    private final TaskRepository tasks;

    public TrainerController(TrainerRepository trainers, TaskRepository tasks) {
        this.trainers = trainers;
        this.tasks = tasks;
    }

    public void routes(Javalin app) {
        app.get("/api/trainers", ctx -> ctx.json(trainers.findAll()));
        app.get("/api/trainers/{id}/tasks", ctx -> {
            UUID trainerId = UUID.fromString(ctx.pathParam("id"));
            ctx.json(tasks.findByTrainer(trainerId, false));
        });
    }
}

