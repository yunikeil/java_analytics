package dev.local.analytics.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.local.analytics.config.AppConfig;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public class Database implements AutoCloseable {
    private final HikariDataSource dataSource;

    private Database(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Database create(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.jdbcUser());
        hikari.setPassword(config.jdbcPassword());
        hikari.setMaximumPoolSize(8);
        hikari.setPoolName("analytics-trainer-pool");
        return new Database(new HikariDataSource(hikari));
    }

    public void migrate() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

