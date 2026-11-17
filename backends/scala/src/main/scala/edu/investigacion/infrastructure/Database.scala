package edu.investigacion.infrastructure

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

object Database:

  def createDataSource(config: AppConfig): HikariDataSource =
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.databaseUrl)
    hikariConfig.setUsername(config.databaseUser)
    hikariConfig.setPassword(config.databasePassword)
    hikariConfig.setMaximumPoolSize(config.dbPoolSize)
    hikariConfig.setMinimumIdle(2)
    hikariConfig.setPoolName("InvestigacionHikariPool")
    hikariConfig.setConnectionTimeout(30000)
    hikariConfig.setIdleTimeout(600000)
    hikariConfig.setMaxLifetime(1800000)
    new HikariDataSource(hikariConfig)
