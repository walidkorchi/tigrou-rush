package io.github.rush.database;

import io.github.rush.Main;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;

public class DatabaseManager {

    private final EntityManagerFactory entityManagerFactory;
    private final Main plugin;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        this.entityManagerFactory = createEntityManagerFactory();
    }

    private EntityManagerFactory createEntityManagerFactory() {
        Configuration configuration = new Configuration();

        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", getConnectionUrl());
        configuration.setProperty("hibernate.connection.username", getUsername());
        configuration.setProperty("hibernate.connection.password", getPassword());
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
        configuration.setProperty("hibernate.show_sql", "false");
        configuration.setProperty("hibernate.connection.pool_size", "10");

        configuration.addAnnotatedClass(io.github.rush.statistics.PlayerStatistic.class);

        return configuration.buildSessionFactory();
    }

    private String getConnectionUrl() {
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 5432);
        String database = plugin.getConfig().getString("database.name", "rush");
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }

    private String getUsername() {
        return plugin.getConfig().getString("database.username", "rush");
    }

    private String getPassword() {
        return plugin.getConfig().getString("database.password", "rush");
    }

    public EntityManager getEntityManager() {
        return entityManagerFactory.createEntityManager();
    }

    public void close() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }
}
