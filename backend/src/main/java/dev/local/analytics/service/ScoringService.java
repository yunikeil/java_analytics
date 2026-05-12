package dev.local.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.analytics.model.Task;
import dev.local.analytics.model.TaskType;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ScoringService {
    @SuppressWarnings("unused")
    private final ObjectMapper mapper;

    public ScoringService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ScoreResult score(Task task, JsonNode answer) {
        if (answer == null || answer.isNull()) {
            throw new IllegalArgumentException("Answer is required");
        }
        return switch (task.type()) {
            case TEST -> scoreTest(task, answer);
            case ERROR_SEARCH -> scoreErrorSearch(task, answer);
            case OPEN -> scoreOpen(task, answer);
        };
    }

    private ScoreResult scoreTest(Task task, JsonNode answer) {
        Set<String> expected = stringSet(task.correctAnswer().path("correct"));
        Set<String> selected = stringSet(answer.has("selected") ? answer.path("selected") : answer);
        boolean correct = expected.equals(selected);
        double score = correct ? task.maxScore() : 0;
        return new ScoreResult(score, task.maxScore(), correct,
                correct ? "Ответ совпадает с эталоном" : "Набор выбранных вариантов не совпал с эталоном");
    }

    private ScoreResult scoreErrorSearch(Task task, JsonNode answer) {
        Set<String> expected = stringSet(task.correctAnswer().path("errors"));
        Set<String> selected = stringSet(answer.has("errors") ? answer.path("errors") : answer);
        if (expected.isEmpty()) {
            return new ScoreResult(0, task.maxScore(), false, "Для задания не задан эталон");
        }
        long hits = selected.stream().filter(expected::contains).count();
        long falsePositives = selected.stream().filter(item -> !expected.contains(item)).count();
        double ratio = Math.max(0, (hits - falsePositives * 0.5) / expected.size());
        double score = round(task.maxScore() * Math.min(1, ratio));
        boolean correct = score == task.maxScore();
        return new ScoreResult(score, task.maxScore(), correct,
                "Найдено корректных ошибок: " + hits + " из " + expected.size() + ", лишних отметок: " + falsePositives);
    }

    private ScoreResult scoreOpen(Task task, JsonNode answer) {
        JsonNode sandboxExpected = task.correctAnswer().path("sandbox");
        if (!sandboxExpected.isMissingNode() && !sandboxExpected.isNull()) {
            return scoreSandbox(task, answer, sandboxExpected);
        }
        return new ScoreResult(0, task.maxScore(), false, "Для открытого задания не настроена точная проверка");
    }

    private ScoreResult scoreSandbox(Task task, JsonNode answer, JsonNode expected) {
        JsonNode actual = answer.path("sandbox").path("result");
        if (actual.isMissingNode() || actual.isNull()) {
            return new ScoreResult(0, task.maxScore(), false, "Сначала запустите sandbox и отправьте результат проверки");
        }
        String kind = expected.path("kind").asText("sql");
        boolean correct = "python".equals(kind)
                ? normalizeText(actual.path("stdout").asText()).equals(normalizeText(expected.path("stdout").asText()))
                : normalizedRows(actual.path("rows")).equals(normalizedRows(expected.path("rows")));
        return new ScoreResult(
                correct ? task.maxScore() : 0,
                task.maxScore(),
                correct,
                correct ? "Данные совпали с эталоном" : "Данные не совпали с эталоном"
        );
    }

    private Set<String> stringSet(JsonNode node) {
        Set<String> values = new HashSet<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        } else {
            values.add(node.asText());
        }
        return values;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private List<String> normalizedRows(JsonNode rows) {
        List<String> normalized = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            rows.forEach(row -> normalized.add(normalizeJson(row)));
        }
        normalized.sort(Comparator.naturalOrder());
        return normalized;
    }

    private String normalizeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(name -> fields.add(name + ":" + normalizeJson(node.get(name))));
            fields.sort(Comparator.naturalOrder());
            return "{" + String.join(",", fields) + "}";
        }
        if (node.isArray()) {
            List<String> items = new ArrayList<>();
            node.forEach(item -> items.add(normalizeJson(item)));
            return "[" + String.join(",", items) + "]";
        }
        if (node.isNumber()) {
            return String.valueOf(node.asDouble());
        }
        return normalizeText(node.asText());
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replace("\r\n", "\n");
    }

    public record ScoreResult(double score, int maxScore, boolean correct, String feedback) {
    }
}
