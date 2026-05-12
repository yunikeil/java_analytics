package dev.local.analytics.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.analytics.model.Trainer;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrainerRepository {
    private final DataSource dataSource;
    @SuppressWarnings("unused")
    private final ObjectMapper mapper;

    public TrainerRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public List<Trainer> findAll() {
        String sql = """
                SELECT tr.id, tr.title, tr.description, tr.difficulty, COUNT(t.id)::int AS task_count
                FROM trainers tr
                LEFT JOIN tasks t ON t.trainer_id = tr.id
                GROUP BY tr.id
                ORDER BY tr.created_at, tr.title
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Trainer> trainers = new ArrayList<>();
            while (rs.next()) {
                trainers.add(new Trainer(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("difficulty"),
                        rs.getInt("task_count")
                ));
            }
            return trainers;
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }
}
