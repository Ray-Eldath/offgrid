package ray.eldath.offgrid.handler

import org.apache.commons.validator.routines.EmailValidator
import org.http4k.contract.ContractRoute
import org.http4k.contract.Tag
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.core.ContentType.Companion.APPLICATION_JSON
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.format.Jackson.auto
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.select
import ray.eldath.offgrid.component.ApiException
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.Argon2
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.dao.Authorizations
import ray.eldath.offgrid.dao.Users
import java.util.*

class Login(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class LoginRequest(val email: String, val password: String) {
        init {
            require(EmailValidator.getInstance().isValid(email))
        }
    }

    data class LoginResponse(val bearer: String, val expireIn: Long)

    private val expireIn = (BearerSecurity.EXPIRY_MINUTES * 60).toLong()
    private val requestLens = Body.auto<LoginRequest>("Login essentials.").toLens()
    private val responseLens = Body.auto<LoginResponse>("The bearer and expire.").toLens()

    private fun handler(): HttpHandler = { req: Request ->
        val json = requestLens(req)
        val email = json.email
        val plainPassword = json.password.toByteArray()

        if (!EmailValidator.getInstance().isValid(email))
            throw ApiException(501, "invalid email address")

        val auth: Query = (Users innerJoin Authorizations).select { Users.email eq email }.fetchSize(1)
        if (auth.count() == 0)
            throw ApiException(502, "user with given email not found")

        auth.forEach {
            if (!it[Users.emailConfirmed])
                throw ApiException(401, "unconfirmed email address", UNAUTHORIZED)
            else if (!Argon2.verify(it[Authorizations.hashedPassword].bytes, plainPassword))
                throw ApiException(401, "incorrect password", UNAUTHORIZED)
        }

        Response(OK)
    }

    override fun compile(): ContractRoute {

        return "/login" meta {
            summary = "Login, use Bearer Authorization"
            tags += Tag("user", "authorization")

            consumes += APPLICATION_JSON
            produces += APPLICATION_JSON

            returning(OK, responseLens to LoginResponse(UUID.randomUUID().toString(), expireIn))
            exception(BAD_REQUEST, 501, "invalid email address")
            exception(UNAUTHORIZED, 402, "unconfirmed email address")
        } bindContract Method.POST to handler()
    }
}