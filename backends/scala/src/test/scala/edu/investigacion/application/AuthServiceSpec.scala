package edu.investigacion.application

import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.*
import edu.investigacion.doubles.InMemoryUserRepository
import edu.investigacion.infrastructure.{BcryptPasswordHasher, JwtTokenService}

class AuthServiceSpec extends AnyFlatSpec with Matchers:

  def createFixture() =
    val userRepo = new InMemoryUserRepository()
    val hasher = new BcryptPasswordHasher(cost = 10)
    val tokenService = new JwtTokenService("secreto_para_pruebas_unitarias_de_alta_entropia_1234567890", 24L)
    val authService = new AuthService(userRepo, hasher, tokenService)
    (userRepo, hasher, tokenService, authService)

  "AuthService.register" should "register a new user with role USER and return user and token" in {
    val (_, _, _, authService) = createFixture()
    val input = RegisterInput("Investigador Uno", "inv1@test.com", "Password123")
    val res = authService.register(input)
    res.isRight shouldBe true
    val (user, token) = res.toOption.get
    user.name shouldBe "Investigador Uno"
    user.email shouldBe "inv1@test.com"
    user.role shouldBe Role.USER
    token should not be empty
  }

  it should "return ConflictError (409) when email already exists" in {
    val (userRepo, hasher, _, authService) = createFixture()
    val existing = User(
      id = UUID.randomUUID(),
      name = "Existente",
      email = "existente@test.com",
      passwordHash = hasher.hash("Password123"),
      role = Role.USER,
      state = EntityState.ACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    userRepo.seed(existing)

    val input = RegisterInput("Otro", "existente@test.com", "Password123")
    val res = authService.register(input)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 409
  }

  "AuthService.login" should "successfully authenticate active user with correct password" in {
    val (userRepo, hasher, _, authService) = createFixture()
    val user = User(
      id = UUID.randomUUID(),
      name = "Usuario Activo",
      email = "activo@test.com",
      passwordHash = hasher.hash("Password123"),
      role = Role.USER,
      state = EntityState.ACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    userRepo.seed(user)

    val res = authService.login(LoginInput("activo@test.com", "Password123"))
    res.isRight shouldBe true
    val (loggedUser, token) = res.toOption.get
    loggedUser.id shouldBe user.id
    token should not be empty
  }

  it should "reject login with wrong password using generic 'Credenciales inválidas' (401)" in {
    val (userRepo, hasher, _, authService) = createFixture()
    val user = User(
      id = UUID.randomUUID(),
      name = "Usuario Activo",
      email = "activo@test.com",
      passwordHash = hasher.hash("Password123"),
      role = Role.USER,
      state = EntityState.ACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    userRepo.seed(user)

    val res = authService.login(LoginInput("activo@test.com", "WrongPassword999"))
    res.isLeft shouldBe true
    val err = res.left.toOption.get
    err.statusCode shouldBe 401
    err.message shouldBe "Credenciales inválidas"
  }

  it should "reject login for INACTIVE user with generic 401" in {
    val (userRepo, hasher, _, authService) = createFixture()
    val user = User(
      id = UUID.randomUUID(),
      name = "Usuario Inactivo",
      email = "inactivo@test.com",
      passwordHash = hasher.hash("Password123"),
      role = Role.USER,
      state = EntityState.INACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    userRepo.seed(user)

    val res = authService.login(LoginInput("inactivo@test.com", "Password123"))
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 401
  }
