package edu.investigacion.infrastructure

import edu.investigacion.analytics.*
import edu.investigacion.domain.AnalyticsRepository
import java.sql.ResultSet
import java.time.ZoneOffset
import javax.sql.DataSource

class JdbcAnalyticsRepository(private val dataSource: DataSource) : AnalyticsRepository {

    override fun getActiveCases(): List<CaseRec> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT id, status, incident_date FROM cases WHERE state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val list = mutableListOf<CaseRec>()
                while (rs.next()) {
                    val epochDay = rs.getTimestamp("incident_date").toInstant().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    list.add(
                        CaseRec(
                            id = rs.getString("id").lowercase(),
                            status = CaseStatus.valueOf(rs.getString("status")),
                            incidentEpochDay = epochDay
                        )
                    )
                }
                list
            }
        }
    }

    override fun getActiveSuspects(): List<SuspectRec> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT id, case_id, suspicion_level, status FROM suspects WHERE state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val list = mutableListOf<SuspectRec>()
                while (rs.next()) {
                    list.add(
                        SuspectRec(
                            id = rs.getString("id").lowercase(),
                            caseId = rs.getString("case_id").lowercase(),
                            suspicionLevel = rs.getLong("suspicion_level"),
                            status = SuspectStatus.valueOf(rs.getString("status"))
                        )
                    )
                }
                list
            }
        }
    }

    override fun getActiveEvidences(): List<EvidenceRec> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT case_id, suspect_id, type, relevance, collected_at FROM evidences WHERE state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val list = mutableListOf<EvidenceRec>()
                while (rs.next()) {
                    val epochDay = rs.getTimestamp("collected_at").toInstant().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    val susId = rs.getString("suspect_id")?.lowercase()
                    list.add(
                        EvidenceRec(
                            caseId = rs.getString("case_id").lowercase(),
                            suspectId = susId,
                            type = EvidenceType.valueOf(rs.getString("type")),
                            relevance = rs.getLong("relevance"),
                            collectedEpochDay = epochDay,
                            active = true
                        )
                    )
                }
                list
            }
        }
    }
}
