package edu.investigacion.infrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class Database {
    private Database() {}

    public static HikariDataSource createDataSource(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl());
        hc.setUsername(config.dbUser());
        hc.setPassword(config.dbPassword());
        hc.setMaximumPoolSize(config.dbPoolSize());
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(30000);
        hc.setIdleTimeout(600000);
        hc.setMaxLifetime(1800000);
        hc.setConnectionInitSql("SELECT 1");
        hc.setPoolName("HikariPool-JavaBackend");

        return new HikariDataSource(hc);
    }
}
