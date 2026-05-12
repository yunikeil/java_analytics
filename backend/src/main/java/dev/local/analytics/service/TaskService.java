package dev.local.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import dev.local.analytics.dto.TaskDtos.SubmitResponse;
import dev.local.analytics.model.Task;
import dev.local.analytics.repository.AttemptRepository;
import dev.local.analytics.repository.ProgressRepository;
import dev.local.analytics.repository.TaskRepository;

import java.util.UUID;

public class TaskService {
    private final TaskRepository tasks;
    private final AttemptRepository attempts;
    private final ProgressRepository progress;
    private final ScoringService scoring;

    public TaskService(TaskRepository tasks, AttemptRepository attempts, ProgressRepository progress, ScoringService scoring) {
        this.tasks = tasks;
        this.attempts = attempts;
        this.progress = progress;
        this.scoring = scoring;
    }

    public SubmitResponse submit(UUID userId, UUID taskId, JsonNode answer) {
        Task task = tasks.findById(taskId, true).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        ScoringService.ScoreResult result = scoring.score(task, answer);
        attempts.create(userId, taskId, answer, result.score(), result.maxScore(), result.correct(), result.feedback());
        progress.recompute(userId, task.trainerId());
        return new SubmitResponse(result.score(), result.maxScore(), result.correct(), result.feedback());
    }
}

