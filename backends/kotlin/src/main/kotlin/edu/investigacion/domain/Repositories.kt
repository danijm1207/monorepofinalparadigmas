package edu.investigacion.domain

interface UserRepository {
    fun findById(id: String): User?
    fun findByEmail(email: String): User?
    fun create(user: User): Result<User>
    fun findAll(page: Int, limit: Int, query: String?, role: Role?): Page<UserPublic>
    fun deactivate(id: String): Result<Unit>
}

interface CaseRepository {
    fun findById(id: String): Case?
    fun create(case: Case): Result<Case>
    fun update(id: String, title: String, description: String, location: String, incidentDate: String, status: CaseStatus, imageUrl: String?): Result<Case>
    fun delete(id: String): Result<Unit>
    fun findAll(page: Int, limit: Int, query: String?, status: CaseStatus?, from: String?, to: String?, ownerId: String?): Page<Case>
}

interface SuspectRepository {
    fun findById(id: String): Suspect?
    fun create(suspect: Suspect): Result<Suspect>
    fun update(id: String, fullName: String, alias: String?, description: String?, suspicionLevel: Int, status: SuspectStatus, photoUrl: String?): Result<Suspect>
    fun delete(id: String): Result<Unit>
    fun findAll(page: Int, limit: Int, caseId: String?, status: SuspectStatus?, query: String?, allowedCaseIds: List<String>?): Page<Suspect>
}

interface EvidenceRepository {
    fun findById(id: String): Evidence?
    fun create(evidence: Evidence): Result<Evidence>
    fun update(id: String, suspectId: String?, title: String, description: String, type: EvidenceType, collectedAt: String, relevance: Int, imageUrl: String?): Result<Evidence>
    fun delete(id: String): Result<Unit>
    fun findAll(page: Int, limit: Int, caseId: String?, suspectId: String?, type: EvidenceType?, query: String?, allowedCaseIds: List<String>?): Page<Evidence>
}

interface AnalyticsRepository {
    fun getActiveCases(): List<edu.investigacion.analytics.CaseRec>
    fun getActiveSuspects(): List<edu.investigacion.analytics.SuspectRec>
    fun getActiveEvidences(): List<edu.investigacion.analytics.EvidenceRec>
}
