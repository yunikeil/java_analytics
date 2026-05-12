package dev.local.analytics.repository;

import dev.local.analytics.model.User;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class UserRepository {
    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public User create(String email, String passwordHash, String fullName) {
        String sql = """
                INSERT INTO users(email, password_hash, full_name)
                VALUES (?, ?, ?)
                RETURNING id, email, full_name, role, created_at
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.toLowerCase());
            statement.setString(2, passwordHash);
            statement.setString(3, fullName);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return mapUser(rs);
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("User already exists");
            }
            throw new RepositoryException(e);
        }
    }

    public Optional<UserWithPassword> findByEmailWithPassword(String email) {
        String sql = "SELECT id, email, password_hash, full_name, role, created_at FROM users WHERE email = ?";
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserWithPassword(mapUser(rs), rs.getString("password_hash")));
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    public Optional<User> findById(UUID id) {
        String sql = "SELECT id, email, full_name, role, created_at FROM users WHERE id = ?";
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public record UserWithPassword(User user, String passwordHash) {
    }
}

