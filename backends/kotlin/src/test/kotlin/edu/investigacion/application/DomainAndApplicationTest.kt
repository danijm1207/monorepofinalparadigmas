package edu.investigacion.application

import edu.investigacion.domain.*
import edu.investigacion.infrastructure.FileStorageService
import edu.investigacion.infrastructure.PasswordHasher
import edu.investigacion.infrastructure.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class DomainAndApplicationTest {

    private lateinit var userRepo: InMemoryUserRepository
    private lateinit var caseRepo: InMemoryCaseRepository
    private lateinit var suspectRepo: InMemorySuspectRepository
    private lateinit var evidenceRepo: InMemoryEvidenceRepository
    private lateinit var tokenService: TokenService

    private lateinit var authService: AuthService
    private lateinit var caseService: CaseService
    private lateinit var suspectService: SuspectService
    private lateinit var evidenceService: EvidenceService
    private lateinit var userService: UserService

    private val adminId = "00000000-0000-4000-8000-000000000001"
    private val userId = "00000000-0000-4000-8000-000000000002"

    @BeforeEach
    fun setUp() {
        userRepo = InMemoryUserRepository()
        caseRepo = InMemoryCaseRepository()
        suspectRepo = InMemorySuspectRepository()
        evidenceRepo = InMemoryEvidenceRepository()
        tokenService = TokenService("test-secret-key-1234567890-test-key", 24L)

        authService = AuthService(userRepo, tokenService)
        caseService = CaseService(caseRepo)
        suspectService = SuspectService(suspectRepo, caseRepo)
        evidenceService = EvidenceService(evidenceRepo, caseRepo, suspectRepo)
        userService = UserService(userRepo)

        // Seed users
        userRepo.create(
            User(
                id = adminId,
                name = "Administrador",
                email = "admin@investigacion.test",
                passwordHash = PasswordHasher.hash("Admin12345"),
                role = Role.ADMIN,
                state = RecordState.ACTIVE,
                createdAt = "",
                updatedAt = ""
            )
        )

        userRepo.create(
            User(
                id = userId,
                name = "Investigador",
                email = "user@investigacion.test",
                passwordHash = PasswordHasher.hash("User12345"),
                role = Role.USER,
                state = RecordState.ACTIVE,
                createdAt = "",
                updatedAt = ""
            )
        )
    }

    // ==================== AUTH & USERS ====================
    @Test
    fun `registro exitoso crea usuario USER y genera token valido`() {
        val res = authService.register("Nuevo Agente", "agente@test.com", "Agente123")
        assertThat(res).isInstanceOf(Result.Success::class.java)
        val data = (res as Result.Success).value
        assertThat(data.first.role).isEqualTo(Role.USER)
        assertThat(data.first.email).isEqualTo("agente@test.com")
        assertThat(data.second).isNotBlank()

        val tokenPayload = tokenService.verifyToken(data.second)
        assertThat(tokenPayload).isNotNull
        assertThat(tokenPayload?.role).isEqualTo(Role.USER)
    }

    @Test
    fun `registro con email duplicado devuelve conflicto 409`() {
        val res = authService.register("Duplicado", "admin@investigacion.test", "Clave12345")
        assertThat(res).isInstanceOf(Result.Failure::class.java)
        val error = (res as Result.Failure).error
        assertThat(error).isInstanceOf(AppError.Conflict::class.java)
    }

    @Test
    fun `login exitoso con credenciales correctas`() {
        val res = authService.login("admin@investigacion.test", "Admin12345")
        assertThat(res).isInstanceOf(Result.Success::class.java)
        val data = (res as Result.Success).value
        assertThat(data.first.email).isEqualTo("admin@investigacion.test")
        assertThat(data.first.role).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `login con clave incorrecta devuelve Unauthorized con mensaje exacto`() {
        val res = authService.login("admin@investigacion.test", "Incorrecta123")
        assertThat(res).isInstanceOf(Result.Failure::class.java)
        val error = (res as Result.Failure).error
        assertThat(error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(error.message).isEqualTo("Credenciales inválidas")
    }

    @Test
    fun `login con usuario inactivo devuelve Unauthorized con mensaje exacto`() {
        userService.deactivate(userId, adminId)
        val res = authService.login("user@investigacion.test", "User12345")
        assertThat(res).isInstanceOf(Result.Failure::class.java)
        val error = (res as Result.Failure).error
        assertThat(error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(error.message).isEqualTo("Credenciales inválidas")
    }

    @Test
    fun `admin no puede desactivarse a si mismo`() {
        val res = userService.deactivate(adminId, adminId)
        assertThat(res).isInstanceOf(Result.Failure::class.java)
        val error = (res as Result.Failure).error
        assertThat(error).isInstanceOf(AppError.Conflict::class.java)
    }

    // ==================== CASOS & POLITICA DE ACCESO ====================
    @Test
    fun `usuario solo accede a casos de los que es dueno`() {
        val caseRes = caseService.create(
            title = "Caso de Prueba",
            description = "Descripción detallada del caso",
            location = "Distrito 5",
            incidentDate = "2026-05-10T12:00:00.000Z",
            status = "OPEN",
            imageUrl = null,
            currentUserId = userId
        )
        assertThat(caseRes).isInstanceOf(Result.Success::class.java)
        val caseId = (caseRes as Result.Success).value.id

        // Dueño accede ok
        val ownerAccess = caseService.getById(caseId, userId, Role.USER)
        assertThat(ownerAccess).isInstanceOf(Result.Success::class.java)

        // Admin accede ok
        val adminAccess = caseService.getById(caseId, adminId, Role.ADMIN)
        assertThat(adminAccess).isInstanceOf(Result.Success::class.java)

        // Otro usuario recibe 404
        val otherAccess = caseService.getById(caseId, "00000000-0000-4000-8000-000000000099", Role.USER)
        assertThat(otherAccess).isInstanceOf(Result.Failure::class.java)
        assertThat((otherAccess as Result.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    // ==================== SOSPECHOSOS & EVIDENCIAS ====================
    @Test
    fun `no se pueden crear sospechosos ni evidencias en caso CLOSED`() {
        val caseRes = caseService.create(
            title = "Caso Cerrado",
            description = "Descripción del caso que se va a cerrar",
            location = "Distrito Central",
            incidentDate = "2026-04-10T12:00:00.000Z",
            status = "CLOSED",
            imageUrl = null,
            currentUserId = userId
        )
        val caseId = (caseRes as Result.Success).value.id

        // Crear sospechoso -> 409 Conflict
        val susRes = suspectService.create(
            caseId = caseId,
            fullName = "Sospechoso Uno",
            alias = "El Uno",
            description = "Desc",
            suspicionLevel = 50,
            status = "PERSON_OF_INTEREST",
            photoUrl = null,
            currentUserId = userId,
            currentUserRole = Role.USER
        )
        assertThat(susRes).isInstanceOf(Result.Failure::class.java)
        assertThat((susRes as Result.Failure).error).isInstanceOf(AppError.Conflict::class.java)

        // Crear evidencia -> 409 Conflict
        val evRes = evidenceService.create(
            caseId = caseId,
            suspectId = null,
            title = "Evidencia 1",
            description = "Descripción válida",
            type = "PHYSICAL",
            collectedAt = "2026-04-11T12:00:00.000Z",
            relevance = 4,
            imageUrl = null,
            currentUserId = userId,
            currentUserRole = Role.USER
        )
        assertThat(evRes).isInstanceOf(Result.Failure::class.java)
        assertThat((evRes as Result.Failure).error).isInstanceOf(AppError.Conflict::class.java)
    }

    @Test
    fun `suspectId de una evidencia debe pertenecer al mismo caseId`() {
        val case1 = (caseService.create("Caso 1", "Descripción caso uno", "Loc 1", "2026-01-01T00:00:00.000Z", "OPEN", null, userId) as Result.Success).value
        val case2 = (caseService.create("Caso 2", "Descripción caso dos", "Loc 2", "2026-01-01T00:00:00.000Z", "OPEN", null, userId) as Result.Success).value

        val suspectInCase2 = (suspectService.create(case2.id, "Sospechoso C2", null, null, 10, "PERSON_OF_INTEREST", null, userId, Role.USER) as Result.Success).value

        // Crear evidencia en Caso 1 asociando sospechoso de Caso 2 -> 400 Validation
        val evRes = evidenceService.create(
            caseId = case1.id,
            suspectId = suspectInCase2.id,
            title = "Arma",
            description = "Descripción válida de prueba",
            type = "PHYSICAL",
            collectedAt = "2026-01-02T00:00:00.000Z",
            relevance = 5,
            imageUrl = null,
            currentUserId = userId,
            currentUserRole = Role.USER
        )
        assertThat(evRes).isInstanceOf(Result.Failure::class.java)
        assertThat((evRes as Result.Failure).error).isInstanceOf(AppError.Validation::class.java)
    }

    // ==================== VALIDACIONES PURAS ====================
    @Test
    fun `validacion acumula todos los errores de campos en details`() {
        val errors = Validation.validateCase(
            title = "A", // muy corto
            description = "corta", // muy corta
            location = "L", // muy corto
            incidentDate = "fecha_invalida",
            status = "ESTADO_DESCONOCIDO",
            imageUrl = "no-es-url"
        )
        assertThat(errors).hasSize(6)
        val fields = errors.map { it.field }
        assertThat(fields).containsExactlyInAnyOrder("title", "description", "location", "incidentDate", "status", "imageUrl")
    }

    @Test
    fun `evidencia con fecha futura es rechazada`() {
        val future = Instant.now().plusSeconds(86400).toString()
        val errors = Validation.validateEvidence(
            caseId = "00000000-0000-4000-8000-000000000001",
            suspectId = null,
            title = "Evidencia Futura",
            description = "Descripción válida",
            type = "DOCUMENT",
            collectedAt = future,
            relevance = 3,
            imageUrl = null
        )
        assertThat(errors.any { it.field == "collectedAt" }).isTrue()
    }

    // ==================== SERVICIO DE ALMACENAMIENTO DE ARCHIVOS ====================
    @Test
    fun `deteccion de firmas magicas para archivos binarios`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(), 0, 0, 0, 0)
        assertThat(FileStorageService.detectExtension(pngBytes)).isEqualTo("png")

        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        assertThat(FileStorageService.detectExtension(jpegBytes)).isEqualTo("jpg")

        val invalidBytes = "Texto plano no binario permitido".toByteArray()
        assertThat(FileStorageService.detectExtension(invalidBytes)).isNull()
    }
}
