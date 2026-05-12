package dev.local.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.analytics.dto.TaskDtos.SubmitRequest;
import dev.local.analytics.repository.TaskRepository;
import dev.local.analytics.service.TaskService;
import io.javalin.Javalin;

import java.util.UUID;

public class TaskController {
    private final ObjectMapper mapper;
    private final TaskRepository tasks;
    private final TaskService taskService;

    public TaskController(ObjectMapper mapper, TaskRepository tasks, TaskService taskService) {
        this.mapper = mapper;
        this.tasks = tasks;
        this.taskService = taskService;
    }

    public void routes(Javalin app) {
        app.get("/api/tasks/{id}", ctx -> {
            UUID taskId = UUID.fromString(ctx.pathParam("id"));
            ctx.json(tasks.findById(taskId, false).orElseThrow(() -> new IllegalArgumentException("Task not found")));
        });
        app.get("/api/tasks/{id}/solution", ctx -> {
            UUID taskId = UUID.fromString(ctx.pathParam("id"));
            ctx.json(tasks.findById(taskId, true).orElseThrow(() -> new IllegalArgumentException("Task not found")));
        });
        app.post("/api/tasks/{id}/submit", ctx -> {
            UUID taskId = UUID.fromString(ctx.pathParam("id"));
            UUID userId = (UUID) ctx.attribute("userId");
            SubmitRequest request = mapper.readValue(ctx.body(), SubmitRequest.class);
            ctx.json(taskService.submit(userId, taskId, request.answer()));
        });
    }
}
