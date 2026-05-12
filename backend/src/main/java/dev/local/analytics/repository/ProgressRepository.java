package dev.local.analytics.repository;

import dev.local.analytics.model.Progress;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProgressRepository {
    private final DataSource dataSource;

    public ProgressRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void recompute(UUID userId, UUID trainerId) {
        String sql = """
                WITH best_attempts AS (
                    SELECT DISTINCT ON (a.task_id)
                           a.task_id, a.score, a.max_score
                    FROM attempts a
                    JOIN tasks t ON t.id = a.task_id
                    WHERE a.user_id = ? AND t.trainer_id = ?
                    ORDER BY a.task_id, a.score DESC, a.created_at DESC
                ), totals AS (
                    SELECT
                        COUNT(*)::int AS completed_tasks,
                        COALESCE(SUM(score), 0)::numeric AS total_score,
                        COALESCE(SUM(max_score), 0)::int AS max_score
                    FROM best_attempts
                )
                INSERT INTO progress(user_id, trainer_id, completed_tasks, total_score, max_score, updated_at)
                SELECT ?, ?, completed_tasks, total_score, max_score, now()
                FROM totals
                ON CONFLICT (user_id, trainer_id) DO UPDATE SET
                    completed_tasks = EXCLUDED.completed_tasks,
                    total_score = EXCLUDED.total_score,
                    max_score = EXCLUDED.max_score,
                    updated_at = now()
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setObject(2, trainerId);
            statement.setObject(3, userId);
            statement.setObject(4, trainerId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    public List<Progress> findByUser(UUID userId) {
        String sql = """
                SELECT p.user_id, p.trainer_id, tr.title AS trainer_title, p.completed_tasks,
                       (SELECT COUNT(*)::int FROM tasks t WHERE t.trainer_id = p.trainer_id) AS total_tasks,
                       p.total_score, p.max_score, p.updated_at
                FROM progress p
                JOIN trainers tr ON tr.id = p.trainer_id
                WHERE p.user_id = ?
                ORDER BY tr.title
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Progress> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(new Progress(
                            rs.getObject("user_id", UUID.class),
                            rs.getObject("trainer_id", UUID.class),
                            rs.getString("trainer_title"),
                            rs.getInt("completed_tasks"),
                            rs.getInt("total_tasks"),
                            rs.getDouble("total_score"),
                            rs.getInt("max_score"),
                            rs.getTimestamp("updated_at").toInstant()
                    ));
                }
                return items;
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }
}

