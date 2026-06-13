package io.github.rush.storage;

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
        final Configuration configuration = new Configuration();

        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", getConnectionUrl());
        configuration.setProperty("hibernate.connection.username", getUsername());
        configuration.setProperty("hibernate.connection.password", getPassword());
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
        configuration.setProperty("hibernate.show_sql", "false");
        configuration.setProperty("hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        configuration.setProperty("hibernate.hikari.minimumIdle", "2");
        configuration.setProperty("hibernate.hikari.maximumPoolSize", "10");
        configuration.setProperty("hibernate.hikari.idleTimeout", "30000");

        configuration.addAnnotatedClass(io.github.rush.storage.PlayerStatisticManager.PlayerStatistic.class);
        configuration.addAnnotatedClass(io.github.rush.storage.PlayerLevelManager.PlayerLevel.class);
        configuration.addAnnotatedClass(io.github.rush.storage.PlayerSettingsManager.PlayerSettings.class);

        return configuration.buildSessionFactory();
    }

    private String getConnectionUrl() {
        final String host = plugin.getConfig().getString("database.host", "localhost");
        final int port = plugin.getConfig().getInt("database.port", 5432);
        final String database = plugin.getConfig().getString("database.name", "rush");

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
        if (entityManagerFactory != null && entityManagerFactory.isOpen())
            entityManagerFactory.close();
    }

}
