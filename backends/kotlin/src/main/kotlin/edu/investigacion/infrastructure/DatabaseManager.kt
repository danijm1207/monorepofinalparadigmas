package edu.investigacion.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

object DatabaseManager {
    fun createDataSource(config: AppConfig): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            username = config.databaseUser
            password = config.databasePass
            maximumPoolSize = config.dbPoolSize
            minimumIdle = config.dbPoolSize
            isAutoCommit = true
        }
        return HikariDataSource(hikariConfig)
    }

    fun <T> withConnection(dataSource: DataSource, block: (Connection) -> T): T {
        return dataSource.connection.use(block)
    }

    fun <T> withTransaction(dataSource: DataSource, block: (Connection) -> T): T {
        return dataSource.connection.use { conn ->
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = prevAutoCommit
            }
        }
    }
}
