package edu.investigacion.infrastructure

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import scala.util.Using
import edu.investigacion.analytics.{CaseRec, CaseStatus as AnalyticsCaseStatus, EvidenceRec, EvidenceType as AnalyticsEvidenceType, SuspectRec, SuspectStatus as AnalyticsSuspectStatus}
import edu.investigacion.domain.*

class JdbcUserRepository(dataSource: DataSource) extends UserRepository:

  private def mapUser(rs: ResultSet): User =
    User(
      id = UUID.fromString(rs.getString("id")),
      name = rs.getString("name"),
      email = rs.getString("email"),
      passwordHash = rs.getString("password_hash"),
      role = Role.valueOf(rs.getString("role")),
      state = EntityState.valueOf(rs.getString("state")),
      createdAt = rs.getTimestamp("created_at").toInstant,
      updatedAt = rs.getTimestamp("updated_at").toInstant
    )

  override def findById(id: UUID): Option[User] =
    Using.resource(dataSource.getConnection) { conn =>
      val sql = "SELECT * FROM users WHERE id = ? AND state = 'ACTIVE'"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, id)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Some(mapUser(rs)) else None
        }
      }
    }

  override def findByEmail(email: String): Option[User] =
    Using.resource(dataSource.getConnection) { conn =>
      val sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?) AND state = 'ACTIVE'"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, email.trim.toLowerCase)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Some(mapUser(rs)) else None
        }
      }
    }

  override def create(user: User): User =
    Using.resource(dataSource.getConnection) { conn =>
      val sql =
        """INSERT INTO users (id, name, email, password_hash, role, state, created_at, updated_at)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, user.id)
        ps.setString(2, user.name)
        ps.setString(3, user.email.toLowerCase)
        ps.setString(4, user.passwordHash)
        ps.setString(5, user.role.toString)
        ps.setString(6, user.state.toString)
        ps.setTimestamp(7, Timestamp.from(user.createdAt))
        ps.setTimestamp(8, Timestamp.from(user.updatedAt))
        ps.executeUpdate()
        user
      }
    }

  override def findAll(filter: UserFilter, page: PageRequest): PageResult[User] =
    Using.resource(dataSource.getConnection) { conn =>
      val baseWhere = List("state = 'ACTIVE'")
      val qWhere = filter.q.filter(_.nonEmpty).map(_ => "(LOWER(name) LIKE ? OR LOWER(email) LIKE ?)")
      val roleWhere = filter.role.map(_ => "role = ?")
      val whereClauses = baseWhere ++ qWhere ++ roleWhere
      val whereSql = "WHERE " + whereClauses.mkString(" AND ")

      // Count query
      val countSql = s"SELECT COUNT(*) FROM users $whereSql"
      val total = Using.resource(conn.prepareStatement(countSql)) { ps =>
        var idx = 1
        filter.q.filter(_.nonEmpty).foreach { term =>
          val pattern = s"%${term.toLowerCase}%"
          ps.setString(idx, pattern); idx += 1
          ps.setString(idx, pattern); idx += 1
        }
        filter.role.foreach { r =>
          ps.setString(idx, r.toString); idx += 1
        }
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then rs.getLong(1) else 0L
        }
      }

      val offset = (page.page - 1) * page.limit
      val selectSql =
        s"""SELECT * FROM users $whereSql
           |ORDER BY created_at DESC, id ASC
           |LIMIT ? OFFSET ?""".stripMargin

      val data = Using.resource(conn.prepareStatement(selectSql)) { ps =>
        var idx = 1
        filter.q.filter(_.nonEmpty).foreach { term =>
          val pattern = s"%${term.toLowerCase}%"
          ps.setString(idx, pattern); idx += 1
          ps.setString(idx, pattern); idx += 1
        }
        filter.role.foreach { r =>
          ps.setString(idx, r.toString); idx += 1
        }
        ps.setInt(idx, page.limit); idx += 1
        ps.setInt(idx, offset)

        Using.resource(ps.executeQuery()) { rs =>
          val buffer = List.newBuilder[User]
          while rs.next() do buffer += mapUser(rs)
          buffer.result()
        }
      }

      val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
      PageResult(data, PageMeta(total, page.page, page.limit, totalPages))
    }

  override def deactivate(id: UUID): Boolean =
    Using.resource(dataSource.getConnection) { conn =>
      val sql = "UPDATE users SET state = 'INACTIVE', updated_at = now() WHERE id = ? AND state = 'ACTIVE'"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, id)
        ps.executeUpdate() > 0
      }
    }

class JdbcCaseRepository(dataSource: DataSource) extends CaseRepository:

  private def mapCase(rs: ResultSet): Case =
    Case(
      id = UUID.fromString(rs.getString("id")),
      caseNumber = rs.getLong("case_number"),
      title = rs.getString("title"),
      description = rs.getString("description"),
      location = rs.getString("location"),
      incidentDate = rs.getTimestamp("incident_date").toInstant,
      status = CaseStatus.valueOf(rs.getString("status")),
      imageUrl = Option(rs.getString("image_url")),
      state = EntityState.valueOf(rs.getString("state")),
      ownerId = UUID.fromString(rs.getString("owner_id")),
      createdAt = rs.getTimestamp("created_at").toInstant,
      updatedAt = rs.getTimestamp("updated_at").toInstant
    )

  override def findById(id: UUID): Option[Case] =
    Using.resource(dataSource.getConnection) { conn =>
      val sql = "SELECT * FROM cases WHERE id = ? AND state = 'ACTIVE'"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, id)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Some(mapCase(rs)) else None
        }
      }
    }

  override def create(caseItem: Case): Case =
    Using.resource(dataSource.getConnection) { conn =>
      val sql =
        """INSERT INTO cases (id, title, description, location, incident_date, status, image_url, state, owner_id, created_at, updated_at)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |RETURNING case_number""".stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, caseItem.id)
        ps.setString(2, caseItem.title)
        ps.setString(3, caseItem.description)
        ps.setString(4, caseItem.location)
        ps.setTimestamp(5, Timestamp.from(caseItem.incidentDate))
        ps.setString(6, caseItem.status.toString)
        ps.setString(7, caseItem.imageUrl.orNull)
        ps.setString(8, caseItem.state.toString)
        ps.setObject(9, caseItem.ownerId)
        ps.setTimestamp(10, Timestamp.from(caseItem.createdAt))
        ps.setTimestamp(11, Timestamp.from(caseItem.updatedAt))
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then caseItem.copy(caseNumber = rs.getLong("case_number"))
          else caseItem
        }
      }
    }

  override def update(caseItem: Case): Case =
    Using.resource(dataSource.getConnection) { conn =>
      val sql =
        """UPDATE cases
          |SET title = ?, description = ?, location = ?, incident_date = ?, status = ?, image_url = ?, updated_at = ?
          |WHERE id = ? AND state = 'ACTIVE'""".stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, caseItem.title)
        ps.setString(2, caseItem.description)
        ps.setString(3, caseItem.location)
        ps.setTimestamp(4, Timestamp.from(caseItem.incidentDate))
        ps.setString(5, caseItem.status.toString)
        ps.setString(6, caseItem.imageUrl.orNull)
        ps.setTimestamp(7, Timestamp.from(caseItem.updatedAt))
        ps.setObject(8, caseItem.id)
        ps.executeUpdate()
        caseItem
      }
    }

  override def findAll(filter: CaseFilter, page: PageRequest): PageResult[Case] =
    Using.resource(dataSource.getConnection) { conn =>
      val baseWhere = List("state = 'ACTIVE'")
      val qWhere = filter.q.filter(_.nonEmpty).map(_ => "(LOWER(title) LIKE ? OR LOWER(location) LIKE ?)")
      val statusWhere = filter.status.map(_ => "status = ?")
      val fromWhere = filter.from.map(_ => "incident_date >= ?")
      val toWhere = filter.to.map(_ => "incident_date <= ?")
      val ownerWhere = filter.ownerId.map(_ => "owner_id = ?")

      val whereClauses = baseWhere ++ qWhere ++ statusWhere ++ fromWhere ++ toWhere ++ ownerWhere
      val whereSql = "WHERE " + whereClauses.mkString(" AND ")

      def bindParams(ps: PreparedStatement): Int =
        var idx = 1
        filter.q.filter(_.nonEmpty).foreach { term =>
          val pattern = s"%${term.toLowerCase}%"
          ps.setString(idx, pattern); idx += 1
          ps.setString(idx, pattern); idx += 1
        }
        filter.status.foreach { s =>
          ps.setString(idx, s.toString); idx += 1
        }
        filter.from.foreach { f =>
          ps.setTimestamp(idx, Timestamp.from(f)); idx += 1
        }
        filter.to.foreach { t =>
          ps.setTimestamp(idx, Timestamp.from(t)); idx += 1
        }
        filter.ownerId.foreach { o =>
          ps.setObject(idx, o); idx += 1
        }
        idx

      val countSql = s"SELECT COUNT(*) FROM cases $whereSql"
      val total = Using.resource(conn.prepareStatement(countSql)) { ps =>
        bindParams(ps)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then rs.getLong(1) else 0L
        }
      }

      val offset = (page.page - 1) * page.limit
      val selectSql =
        s"""SELECT * FROM cases $whereSql
           |ORDER BY created_at DESC, id ASC
           |LIMIT ? OFFSET ?""".stripMargin

      val data = Using.resource(conn.prepareStatement(selectSql)) { ps =>
        val nextIdx = bindParams(ps)
        ps.setInt(nextIdx, page.limit)
        ps.setInt(nextIdx + 1, offset)
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = List.newBuilder[Case]
          while rs.next() do buffer += mapCase(rs)
          buffer.result()
        }
      }

      val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
      PageResult(data, PageMeta(total, page.page, page.limit, totalPages))
    }

  override def deactivateCascade(id: UUID): Boolean =
    Using.resource(dataSource.getConnection) { conn =>
      conn.setAutoCommit(false)
      try
        val sqlCase = "UPDATE cases SET state = 'INACTIVE', updated_at = now() WHERE id = ? AND state = 'ACTIVE'"
        val updatedCase = Using.resource(conn.prepareStatement(sqlCase)) { ps =>
          ps.setObject(1, id)
          ps.executeUpdate() > 0
        }

        if updatedCase then
          val sqlSuspects = "UPDATE suspects SET state = 'INACTIVE', updated_at = now() WHERE case_id = ? AND state = 'ACTIVE'"
          Using.resource(conn.prepareStatement(sqlSuspects)) { ps =>
            ps.setObject(1, id)
            ps.executeUpdate()
          }

          val sqlEvidences = "UPDATE evidences SET state = 'INACTIVE', updated_at = now() WHERE case_id = ? AND state = 'ACTIVE'"
          Using.resource(conn.prepareStatement(sqlEvidences)) { ps =>
            ps.setObject(1, id)
            ps.executeUpdate()
          }
          conn.commit()
          true
        else
          conn.rollback()
          false
      catch
        case ex: Throwable =>
          conn.rollback()
          throw ex
      finally
        conn.setAutoCommit(true)
    }

class JdbcSuspectRepository(dataSource: DataSource) extends SuspectRepository:

  private def mapSuspect(rs: ResultSet): Suspect =
    Suspect(
      id = UUID.fromString(rs.getString("id")),
      caseId = UUID.fromString(rs.getString("case_id")),
      fullName = rs.getString("full_name"),
      alias = Option(rs.getString("alias")),
      description = Option(rs.getString("description")),
      suspicionLevel = rs.getInt("suspicion_level"),
      status = SuspectStatus.valueOf(rs.getString("status")),
      photoUrl = Option(rs.getString("photo_url")),
      state = EntityState.valueOf(rs.getString("state")),
      createdAt = rs.getTimestamp("created_at").toInstant,
      updatedAt = rs.getTimestamp("updated_at").toInstant
    )

  override def findById(id: UUID): Option[Suspect] =
    Using.resource(dataSource.getConnection) { conn =>
      val sql = "SELECT * FROM suspects WHERE id = ? AND state = 'ACTIVE'"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, id)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Some(mapSuspect(rs)) else None
        }
      }
    }

  override def create(suspect: Suspect): Suspect =
    Using.resource(dataSource.getConnection) { conn =>
      val sql =
        """INSERT INTO suspects (id, case_id, full_name, alias, description, suspicion_level, status, photo_url, state, created_at, updated_at)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, suspect.id)
        ps.setObject(2, suspect.caseId)
        ps.setString(3, suspect.fullName)
        ps.setString(4, suspect.alias.orNull)
        ps.setString(5, suspect.description.orNull)
        ps.setInt(6, suspect.suspicionLevel)
        ps.setString(7, suspect.status.toString)
        ps.setString(8, suspect.photoUrl.orNull)
        ps.setString(9, suspect.state.toString)
        ps.setTimestamp(10, Timestamp.from(suspect.createdAt))
        ps.setTimestamp(11, Timestamp.from(suspect.updatedAt))
        ps.executeUpdate()
        suspect
      }
    }

  override def update(suspect: Suspect): Suspect =
    Using.resource(dataSource.getConnection) { conn =>
      val sql =
        """UPDATE suspects
          |SET full_name = ?, alias = ?, description = ?, suspicion_level = ?, status = ?, photo_url = ?, updated_at = ?
          |WHERE id = ? AND state = 'ACTIVE'""".stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, suspect.fullName)
        ps.setString(2, suspect.alias.orNull)
        ps.setString(3, suspect.description.orNull)
        ps.setInt(4, suspect.suspicionLevel)
        ps.setString(5, suspect.status.toString)
        ps.setString(6, suspect.photoUrl.orNull)
        ps.setTimestamp(7, Timestamp.from(suspect.updatedAt))
        ps.setObject(8, suspect.id)
        ps.executeUpdate()
        suspect
      }
    }

  override def findAll(filter: SuspectFilter, page: PageRequest): PageResult[Suspect] =
    Using.resource(dataSource.getConnection) { conn =>
      val baseWhere = List("s.state = 'ACTIVE'")
      val caseWhere = filter.caseId.map(_ => "s.case_id = ?")
      val statusWhere = filter.status.map(_ => "s.status = ?")
      val qWhere = filter.q.filter(_.nonEmpty).map(_ => "(LOWER(s.full_name) LIKE ? OR LOWER(s.alias) LIKE ?)")
      val ownerWhere = filter.ownerId.map(_ => "c.owner_id = ?")

      val whereClauses = baseWhere ++ caseWhere ++ statusWhere ++ qWhere ++ ownerWhere
      val fromSql = "FROM suspects s JOIN cases c ON s.case_id = c.id"
      val whereSql = "WHERE " + whereClauses.mkString(" AND ")

      def bindParams(ps: PreparedStatement): Int =
        var idx = 1
        filter.caseId.foreach { cid =>
          ps.setObject(idx, cid); idx += 1
        }
        filter.status.foreach { st =>
          ps.setString(idx, st.toString); idx += 1
        }
        filter.q.filter(_.nonEmpty).foreach { term =>
          val pattern = s"%${term.toLowerCase}%"
          ps.setString(idx, pattern); idx += 1
          ps.setString(idx, pattern); idx += 1
        }
        filter.ownerId.foreach { oid =>
          ps.setObject(idx, oid); idx += 1
        }
        idx

      val countSql = s"SELECT COUNT(*) $fromSql $whereSql"
      val total = Using.resource(conn.prepareStatement(countSql)) { ps =>
        bindParams(ps)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then rs.getLong(1) else 0L
        }
      }

      val offset = (page.page - 1) * page.limit
      val selectSql =
        s"""SELECT s.* $fromSql $whereSql
           |ORDER BY s.created_at DESC, s.id ASC
           |LIMIT ? OFFSET ?""".stripMargin

      val data = Using.resource(conn.prepareStatement(selectSql)) { ps =>
        val nextIdx = bindParams(ps)
        ps.setInt(nextIdx, page.limit)
        ps.setInt(nextIdx + 1, offset)
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = List.newBuilder[Suspect]
          while rs.next() do buffer += mapSuspect(rs)
          buffer.result()
        }
      }

      val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
      PageResult(data, PageMeta(total, page.page, page.limit, totalPages))
    }

  override def deactivateAndUnlinkEvidences(id: UUID): Boolean =
    Using.resource(dataSource.getConnection) { conn =>
      conn.setAutoCommit(false)
      try
        val sqlEvidence = "UPDATE evidences SET suspect_id = NULL, updated_at = now() WHERE suspect_id = ?"
        Using.resource(conn.prepareStatement(sqlEvidence)) { ps =>
          ps.setObject(1, id)
          ps.executeUpdate()
        }

        val sqlSuspect = "UPDATE suspects SET state = 'INACTIVE', updated_at = now() WHERE id = ? AND state = 'ACTIVE'"
        val updated = Using.resource(conn.prepareStatement(sqlSuspect)) { ps =>
          ps.setObject(1, id)
          ps.executeUpdate() > 0
        }

        if updated then
          conn.commit()
          true
        else
          conn.rollback()
          false
      catch
        case ex: Throwable =>
          conn.rollback()
          throw ex
      finally
        conn.setAutoCommit(true)
    }

class JdbcEvidenceRepository(dataSource: DataSource) extends EvidenceRepository:

  private def mapEvidence(rs: ResultSet): Evidence =
    Evidence(
      id = UUID.fromString(rs.getString("id")),
      caseId = UUID.fromString(rs.getString("case_id")),
      suspectId = Option(rs.getString("suspect_id")).map(UUID.fromString),
      title = rs.getString("title"),
      description = rs.getString("description"),
      `type` = EvidenceType.valueOf(rs.getString("type")),
      collectedAt = rs.getTimestamp("collected_at").toInstant,
      relevance = rs.getInt("relevance"),
      imageUrl = Option(rs.getString("image_url")),
      state = EntityState.valueOf(rs.getString("state")),
      createdAt = rs.getTimestamp("created_at").toInstant,
      updatedAt = rs.getTimestamp("updated_at").toInstant
    )

  override def findById(id: UUID): Option[Evidence] =
    Using.resource(dataSource.getConnection) { conn =>
      val sql = "SELECT * FROM evidences WHERE id = ? AND state = 'ACTIVE'"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, id)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Some(mapEvidence(rs)) else None
        }
      }
    }

  override def create(evidence: Evidence): Evidence =
    Using.resource(dataSource.getConnection) { conn =>
      val sql =
        """INSERT INTO evidences (id, case_id, suspect_id, title, description, type, collected_at, relevance, image_url, state, created_at, updated_at)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, evidence.id)
        ps.setObject(2, evidence.caseId)
        evidence.suspectId match
          case Some(sid) => ps.setObject(3, sid)
          case None      => ps.setNull(3, java.sql.Types.OTHER)
        ps.setString(4, evidence.title)
        ps.setString(5, evidence.description)
        ps.setString(6, evidence.`type`.toString)
        ps.setTimestamp(7, Timestamp.from(evidence.collectedAt))
        ps.setInt(8, evidence.relevance)
        ps.setString(9, evidence.imageUrl.orNull)
        ps.setString(10, evidence.state.toString)
        ps.setTimestamp(11, Timestamp.from(evidence.createdAt))
        ps.setTimestamp(12, Timestamp.from(evidence.updatedAt))
        ps.executeUpdate()
        evidence
      }
    }

  override def update(evidence: Evidence): Evidence =
    Using.resource(dataSource.getConnection) { conn =>
      val sql =
        """UPDATE evidences
          |SET suspect_id = ?, title = ?, description = ?, type = ?, collected_at = ?, relevance = ?, image_url = ?, updated_at = ?
          |WHERE id = ? AND state = 'ACTIVE'""".stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        evidence.suspectId match
          case Some(sid) => ps.setObject(1, sid)
          case None      => ps.setNull(1, java.sql.Types.OTHER)
        ps.setString(2, evidence.title)
        ps.setString(3, evidence.description)
        ps.setString(4, evidence.`type`.toString)
        ps.setTimestamp(5, Timestamp.from(evidence.collectedAt))
        ps.setInt(6, evidence.relevance)
        ps.setString(7, evidence.imageUrl.orNull)
        ps.setTimestamp(8, Timestamp.from(evidence.updatedAt))
        ps.setObject(9, evidence.id)
        ps.executeUpdate()
        evidence
      }
    }

  override def findAll(filter: EvidenceFilter, page: PageRequest): PageResult[Evidence] =
    Using.resource(dataSource.getConnection) { conn =>
      val baseWhere = List("e.state = 'ACTIVE'")
      val caseWhere = filter.caseId.map(_ => "e.case_id = ?")
      val suspectWhere = filter.suspectId.map(_ => "e.suspect_id = ?")
      val typeWhere = filter.typeFilter.map(_ => "e.type = ?")
      val qWhere = filter.q.filter(_.nonEmpty).map(_ => "(LOWER(e.title) LIKE ? OR LOWER(e.description) LIKE ?)")
      val ownerWhere = filter.ownerId.map(_ => "c.owner_id = ?")

      val whereClauses = baseWhere ++ caseWhere ++ suspectWhere ++ typeWhere ++ qWhere ++ ownerWhere
      val fromSql = "FROM evidences e JOIN cases c ON e.case_id = c.id"
      val whereSql = "WHERE " + whereClauses.mkString(" AND ")

      def bindParams(ps: PreparedStatement): Int =
        var idx = 1
        filter.caseId.foreach { cid =>
          ps.setObject(idx, cid); idx += 1
        }
        filter.suspectId.foreach { sid =>
          ps.setObject(idx, sid); idx += 1
        }
        filter.typeFilter.foreach { t =>
          ps.setString(idx, t.toString); idx += 1
        }
        filter.q.filter(_.nonEmpty).foreach { term =>
          val pattern = s"%${term.toLowerCase}%"
          ps.setString(idx, pattern); idx += 1
          ps.setString(idx, pattern); idx += 1
        }
        filter.ownerId.foreach { oid =>
          ps.setObject(idx, oid); idx += 1
        }
        idx

      val countSql = s"SELECT COUNT(*) $fromSql $whereSql"
      val total = Using.resource(conn.prepareStatement(countSql)) { ps =>
        bindParams(ps)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then rs.getLong(1) else 0L
        }
      }

      val offset = (page.page - 1) * page.limit
      val selectSql =
        s"""SELECT e.* $fromSql $whereSql
           |ORDER BY e.created_at DESC, e.id ASC
           |LIMIT ? OFFSET ?""".stripMargin

      val data = Using.resource(conn.prepareStatement(selectSql)) { ps =>
        val nextIdx = bindParams(ps)
        ps.setInt(nextIdx, page.limit)
        ps.setInt(nextIdx + 1, offset)
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = List.newBuilder[Evidence]
          while rs.next() do buffer += mapEvidence(rs)
          buffer.result()
        }
      }

      val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
      PageResult(data, PageMeta(total, page.page, page.limit, totalPages))
    }

  override def deactivate(id: UUID): Boolean =
    Using.resource(dataSource.getConnection) { conn =>
      val sql = "UPDATE evidences SET state = 'INACTIVE', updated_at = now() WHERE id = ? AND state = 'ACTIVE'"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, id)
        ps.executeUpdate() > 0
      }
    }

class JdbcAnalyticsRepository(dataSource: DataSource) extends AnalyticsRepository:

  override def fetchActiveRecords(): (List[CaseRec], List[SuspectRec], List[EvidenceRec]) =
    Using.resource(dataSource.getConnection) { conn =>
      val cases = Using.resource(conn.prepareStatement("SELECT id, status, incident_date FROM cases WHERE state = 'ACTIVE'")) { ps =>
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = List.newBuilder[CaseRec]
          while rs.next() do
            val id = rs.getString("id").toLowerCase
            val status = AnalyticsCaseStatus.valueOf(rs.getString("status"))
            val epochDay = rs.getTimestamp("incident_date").toInstant.atZone(ZoneOffset.UTC).toLocalDate.toEpochDay
            buffer += CaseRec(id, status, epochDay)
          buffer.result()
        }
      }

      val suspects = Using.resource(conn.prepareStatement("SELECT id, case_id, suspicion_level, status FROM suspects WHERE state = 'ACTIVE'")) { ps =>
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = List.newBuilder[SuspectRec]
          while rs.next() do
            val id = rs.getString("id").toLowerCase
            val caseId = rs.getString("case_id").toLowerCase
            val suspicion = rs.getInt("suspicion_level").toLong
            val status = AnalyticsSuspectStatus.valueOf(rs.getString("status"))
            buffer += SuspectRec(id, caseId, suspicion, status)
          buffer.result()
        }
      }

      val evidences = Using.resource(conn.prepareStatement("SELECT case_id, suspect_id, type, relevance, collected_at FROM evidences WHERE state = 'ACTIVE'")) { ps =>
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = List.newBuilder[EvidenceRec]
          while rs.next() do
            val caseId = rs.getString("case_id").toLowerCase
            val suspectId = Option(rs.getString("suspect_id")).map(_.toLowerCase)
            val t = AnalyticsEvidenceType.valueOf(rs.getString("type"))
            val relevance = rs.getInt("relevance").toLong
            val epochDay = rs.getTimestamp("collected_at").toInstant.atZone(ZoneOffset.UTC).toLocalDate.toEpochDay
            buffer += EvidenceRec(caseId, suspectId, t, relevance, epochDay, active = true)
          buffer.result()
        }
      }

      (cases, suspects, evidences)
    }
