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
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.select
import ray.eldath.offgrid.compoment.Argon2
import ray.eldath.offgrid.compoment.BearerSecurity
import ray.eldath.offgrid.dao.Authorizations
import ray.eldath.offgrid.dao.Users
import java.util.*

class Login(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class LoginRequest(val email: String, val password: String) {
        init {
            require(EmailValidator.getInstance().isValid(email))
        }
    }

    data class ApiError(val code: Int, val message: String)
    data class LoginResponse(val bearer: String, val expireIn: Long)

    private val expireIn = (BearerSecurity.EXPIRY_MINUTES * 60).toLong()
    private val error = Body.auto<ApiError>().toLens()
    private val requestLens = Body.auto<LoginRequest>("Login essentials.")
    private val responseLens = Body.auto<LoginResponse>("The bearer and expire.").toLens()

    private fun handler(): HttpHandler = { req: Request ->
        req.run {
            val json = Jackson.asJsonObject(req.bodyString())
            val email = json["email"].asText()
            val plainPassword = json["password"].asText().toByteArray()

            if (!EmailValidator.getInstance().isValid(email))
                Response(BAD_REQUEST)

            val auth: Query = (Users innerJoin Authorizations).select { Users.email eq email }.fetchSize(1)
            if (auth.count() == 0)
                return@run Response(UNAUTHORIZED)

            auth.forEach {
                if (!it[Users.emailConfirmed])
                    return@run Response(UNAUTHORIZED).with(
                        error of ApiError(401, "unconfirmed email address")
                    )
                else if (!Argon2.verify(it[Authorizations.hashedPassword].bytes, plainPassword))
                    return@run Response(UNAUTHORIZED).with(
                        error of ApiError(402, "incorrect password")
                    )
            }

            Response(OK)
        }
    }

    override fun compile(): ContractRoute {

        return "/login" meta {
            summary = "Login, use Bearer Authorization"
            tags += Tag("user", "authorization")

            consumes += APPLICATION_JSON
            produces += APPLICATION_JSON

            returning(OK, responseLens to LoginResponse(UUID.randomUUID().toString(), expireIn))
            returning(BAD_REQUEST to "invalid email address")
            returning(UNAUTHORIZED to "incorrect email and/or password")
        } bindContract Method.POST to handler()
    }
}