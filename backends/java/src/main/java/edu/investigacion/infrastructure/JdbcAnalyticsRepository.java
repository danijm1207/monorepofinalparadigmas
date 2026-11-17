package edu.investigacion.infrastructure;

import edu.investigacion.analytics.Models.CaseRec;
import edu.investigacion.analytics.Models.CaseStatus;
import edu.investigacion.analytics.Models.EvidenceRec;
import edu.investigacion.analytics.Models.EvidenceType;
import edu.investigacion.analytics.Models.SuspectRec;
import edu.investigacion.analytics.Models.SuspectStatus;
import edu.investigacion.domain.Repositories.AnalyticsDbRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAnalyticsRepository implements AnalyticsDbRepository {
    private final DataSource dataSource;

    public JdbcAnalyticsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<CaseRec> findActiveCases() {
        String sql = "SELECT id, status, incident_date FROM cases WHERE state = 'ACTIVE'";
        List<CaseRec> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
                CaseStatus st = CaseStatus.valueOf(rs.getString("status"));
                int epochDay = (int) rs.getTimestamp("incident_date").toInstant().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
                list.add(new CaseRec(id.toString().toLowerCase(), st, epochDay));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar casos para analytics", e);
        }
        return list;
    }

    @Override
    public List<SuspectRec> findActiveSuspects() {
        String sql = "SELECT id, case_id, suspicion_level, status FROM suspects WHERE state = 'ACTIVE'";
        List<SuspectRec> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
                UUID caseId = rs.getObject("case_id", UUID.class);
                int suspicion = rs.getInt("suspicion_level");
                SuspectStatus st = SuspectStatus.valueOf(rs.getString("status"));
                list.add(new SuspectRec(id.toString().toLowerCase(), caseId.toString().toLowerCase(), suspicion, st));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar sospechosos para analytics", e);
        }
        return list;
    }

    @Override
    public List<EvidenceRec> findActiveEvidences() {
        String sql = "SELECT case_id, suspect_id, type, relevance, collected_at FROM evidences WHERE state = 'ACTIVE'";
        List<EvidenceRec> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID caseId = rs.getObject("case_id", UUID.class);
                Object sidObj = rs.getObject("suspect_id");
                Optional<String> suspectId = (sidObj != null)
                    ? Optional.of(((UUID) sidObj).toString().toLowerCase())
                    : Optional.empty();
                EvidenceType type = EvidenceType.valueOf(rs.getString("type"));
                int relevance = rs.getInt("relevance");
                int epochDay = (int) rs.getTimestamp("collected_at").toInstant().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();

                list.add(new EvidenceRec(
                    caseId.toString().toLowerCase(),
                    suspectId,
                    type,
                    relevance,
                    epochDay,
                    true // loaded ACTIVE rows
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar evidencias para analytics", e);
        }
        return list;
    }
}
