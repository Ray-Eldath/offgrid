package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.Argon2
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.component.BearerSecurity.bearerToken
import ray.eldath.offgrid.component.BearerSecurity.safeBearerToken
import ray.eldath.offgrid.component.InboundUser
import ray.eldath.offgrid.component.UserRegistrationStatus
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.model.EmailRequest
import ray.eldath.offgrid.model.OutboundUser
import ray.eldath.offgrid.model.toOutbound
import ray.eldath.offgrid.util.*
import java.time.LocalDateTime
import java.util.*

class Login : ContractHandler {
    data class LoginRequest(val email: String, val password: String) : EmailRequest(email)

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class LoginResponse(val bearer: String, val expireIn: Long, val self: OutboundUser)

    private val expireIn = (BearerSecurity.EXPIRY_MINUTES * 60).toLong()

    private val handler: HttpHandler = { req: Request ->
        val json = requestLens(req).also { it.check() }
        val email = json.email
        val plainPassword = json.password.toByteArray()

        val currentBearer = req.bearerToken()
        val current = currentBearer?.let { BearerSecurity.query(it) }

        val inbound: InboundUser =
            if (current == null) { // cannot find corresponding user in registry
                if (currentBearer != null) // but the bearer is set, indicates that the bearer is invalid
                    throw ErrorCodes.AUTH_TOKEN_INVALID_OR_EXPIRED()

                authenticate(email, plainPassword)
            } else current

        val (user, _) = inbound

        transaction {
            val u = Users.USERS

            update(u)
                .set(u.LAST_LOGIN_TIME, LocalDateTime.now())
                .where(u.ID.eq(user.id)).execute()
        }

        val bearer = currentBearer ?: BearerSecurity.authorize(inbound)
        val self =
            user.run {
                OutboundUser(
                    id,
                    user.state.id,
                    username,
                    email,
                    role.toOutbound(),
                    inbound.permissions.toOutbound(),
                    lastLoginTime = lastLoginTime,
                    registerTime = registerTime
                )
            }

        Response(Status.OK)
            .with(responseLens of LoginResponse(bearer, expireIn, self))
    }

    override fun compile(): ContractRoute =
        "/login" meta {
            summary = "Login"
            description = "Use Bearer authorization."
            tags += RouteTag.Authorization
            allJson()

            receiving(requestLens to LoginRequest("alpha.beta@omega.com", "mypassword"))
            returning(
                Status.OK, responseLens to LoginResponse(
                    UUID.randomUUID().toString(), expireIn,
                    OutboundUser.mock
                )
            )
            exception(
                ErrorCodes.INVALID_EMAIL_ADDRESS,
                ErrorCodes.AUTH_TOKEN_INVALID_OR_EXPIRED,
                ErrorCodes.UNCONFIRMED_EMAIL,
                ErrorCodes.USER_NOT_FOUND,
                ErrorCodes.APPLICATION_PENDING,
                ErrorCodes.APPLICATION_REJECTED
            )
        } bindContract Method.POST to handler

    companion object {
        val requestLens = Body.auto<LoginRequest>().toLens()
        val responseLens = Body.auto<LoginResponse>().toLens()

        fun authenticate(email: String, password: ByteArray): InboundUser =
            runState(UserRegistrationStatus.fetchByEmail(email), password).let {
                when (val status = it.second) {
                    is UserRegistrationStatus.Registered -> status.inbound
                    else -> throw it.first!!()
                }
            }

        fun runState(status: UserRegistrationStatus, password: ByteArray): Pair<ErrorCode?, UserRegistrationStatus> =
            runState(status).let {
                when (val run = it.second) {
                    is UserRegistrationStatus.Registered -> {
                        if (!Argon2.verify(run.inbound.user.hashedPassword, password))
                            ErrorCodes.USER_NOT_FOUND to UserRegistrationStatus.NotFound
                        else it
                    }
                    else -> it
                }
            }

        fun runState(status: UserRegistrationStatus): Pair<ErrorCode?, UserRegistrationStatus> =
            when (status) {
                is UserRegistrationStatus.Registered -> {
                    val (user, _) = status.inbound
                    if (user.state == UserState.Banned)
                        ErrorCodes.USER_HAS_BEEN_BANNED
                    else null
                }
                is UserRegistrationStatus.NotFound -> ErrorCodes.USER_NOT_FOUND
                is UserRegistrationStatus.Unconfirmed -> ErrorCodes.UNCONFIRMED_EMAIL
                is UserRegistrationStatus.ApplicationPending -> ErrorCodes.APPLICATION_PENDING
                is UserRegistrationStatus.ApplicationRejected -> ErrorCodes.APPLICATION_REJECTED
            } to status
    }
}

class Logout(private val configuredSecurity: Security) : ContractHandler {

    private fun handler(): HttpHandler = { req ->
        BearerSecurity.invalidate(req.safeBearerToken())
        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/logout" meta {
            summary = "Logout"
            tags += RouteTag.Authorization
            security = configuredSecurity
            allJson()

            returning(Status.OK to "given bearer has been invalidated in the cache")
        } bindContract Method.GET to handler()
}