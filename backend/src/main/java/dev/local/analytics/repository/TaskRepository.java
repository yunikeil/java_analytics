package dev.local.analytics.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.analytics.model.Task;
import dev.local.analytics.model.TaskType;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TaskRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public TaskRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public List<Task> findByTrainer(UUID trainerId, boolean includeAnswers) {
        String sql = """
                SELECT id, trainer_id, type, title, prompt, max_score, options, correct_answer, artifact
                FROM tasks
                WHERE trainer_id = ?
                ORDER BY created_at, title
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, trainerId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Task> tasks = new ArrayList<>();
                while (rs.next()) {
                    tasks.add(mapTask(rs, includeAnswers));
                }
                return tasks;
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    public Optional<Task> findById(UUID id, boolean includeAnswers) {
        String sql = """
                SELECT id, trainer_id, type, title, prompt, max_score, options, correct_answer, artifact
                FROM tasks
                WHERE id = ?
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapTask(rs, includeAnswers)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    public int countByTrainer(UUID trainerId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT COUNT(*)::int FROM tasks WHERE trainer_id = ?")) {
            statement.setObject(1, trainerId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    private Task mapTask(ResultSet rs, boolean includeAnswers) throws SQLException {
        return new Task(
                rs.getObject("id", UUID.class),
                rs.getObject("trainer_id", UUID.class),
                TaskType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("prompt"),
                rs.getInt("max_score"),
                readJson(rs.getString("options")),
                includeAnswers ? readJson(rs.getString("correct_answer")) : null,
                readJson(rs.getString("artifact"))
        );
    }

    private JsonNode readJson(String raw) {
        try {
            return raw == null ? mapper.nullNode() : mapper.readTree(raw);
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }
}
