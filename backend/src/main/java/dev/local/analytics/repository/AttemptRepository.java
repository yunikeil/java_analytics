package dev.local.analytics.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.analytics.model.Attempt;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AttemptRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public AttemptRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public Attempt create(UUID userId, UUID taskId, JsonNode answer, double score, int maxScore, boolean correct, String feedback) {
        String sql = """
                INSERT INTO attempts(user_id, task_id, submitted_answer, score, max_score, is_correct, feedback)
                VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)
                RETURNING id, user_id, task_id, submitted_answer, score, max_score, is_correct, feedback, created_at
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setObject(2, taskId);
            statement.setString(3, mapper.writeValueAsString(answer));
            statement.setDouble(4, score);
            statement.setInt(5, maxScore);
            statement.setBoolean(6, correct);
            statement.setString(7, feedback);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return mapAttempt(rs);
            }
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    public List<Attempt> findByUser(UUID userId) {
        String sql = """
                SELECT id, user_id, task_id, submitted_answer, score, max_score, is_correct, feedback, created_at
                FROM attempts
                WHERE user_id = ?
                ORDER BY created_at DESC
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Attempt> attempts = new ArrayList<>();
                while (rs.next()) {
                    attempts.add(mapAttempt(rs));
                }
                return attempts;
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    private Attempt mapAttempt(ResultSet rs) throws SQLException {
        try {
            return new Attempt(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("task_id", UUID.class),
                    mapper.readTree(rs.getString("submitted_answer")),
                    rs.getDouble("score"),
                    rs.getInt("max_score"),
                    rs.getBoolean("is_correct"),
                    rs.getString("feedback"),
                    rs.getTimestamp("created_at").toInstant()
            );
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }
}

