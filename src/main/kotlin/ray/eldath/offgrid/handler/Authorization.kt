package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import ray.eldath.offgrid.component.*
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.BearerSecurity.bearerToken
import ray.eldath.offgrid.component.BearerSecurity.safeBearerToken
import ray.eldath.offgrid.generated.offgrid.tables.Authorizations
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
        val json = requestLens(req)
        json.check()
        val email = json.email
        val plainPassword = json.password.toByteArray()

        val currentBearer = req.bearerToken()
        val current = currentBearer?.let { BearerSecurity.query(it) }

        val inbound: InboundUser =
            if (current == null) {
                if (currentBearer != null)
                    throw ErrorCodes.AUTH_TOKEN_INVALID_OR_EXPIRED()

                authenticate(email, plainPassword)
            } else current

        val (user, auth, _) = inbound

        transaction {
            val a = Authorizations.AUTHORIZATIONS

            update(a)
                .set(a.LAST_LOGIN_TIME, LocalDateTime.now())
                .where(a.USER_ID.eq(user.id))
                .execute()
        }

        val bearer = currentBearer ?: BearerSecurity.authorize(inbound)
        val self =
            user.run {
                OutboundUser(
                    id,
                    user.state.id,
                    username,
                    email,
                    auth.role.toOutbound(),
                    inbound.permissions.toOutbound(),
                    lastLoginTime = auth.lastLoginTime,
                    registerTime = auth.registerTime
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
                if (it.haveLeft)
                    throw it.leftOrThrow()
                else
                    it.rightOrThrow.rightOrThrow.also { inbound ->
                        if (inbound.user.state == UserState.Banned)
                            throw ErrorCodes.USER_HAS_BEEN_BANNED()
                    }
            }

        fun <L : UserRegistrationStatus, R : ApplicationOrInbound> runState(
            either: Either<L, R>,
            plainPassword: ByteArray
        ): Either<ErrorCode, ApplicationOrInbound> =
            (if (either.haveLeft)
                when (either.leftOrThrow) {
                    UserRegistrationStatus.UNCONFIRMED -> ErrorCodes.UNCONFIRMED_EMAIL
                    UserRegistrationStatus.APPLICATION_PENDING -> ErrorCodes.APPLICATION_PENDING
                    UserRegistrationStatus.APPLICATION_REJECTED -> ErrorCodes.APPLICATION_REJECTED
                    else -> ErrorCodes.USER_NOT_FOUND
                }
            else {
                val i: InboundUser = either.rightOrThrow.rightOrThrow
                if (!Argon2.verify(i.authorization.hashedPassword, plainPassword))
                    ErrorCodes.USER_NOT_FOUND
                else null
            }) or either.right
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