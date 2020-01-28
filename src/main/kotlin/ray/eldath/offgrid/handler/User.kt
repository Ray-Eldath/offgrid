package ray.eldath.offgrid.handler

import org.apache.commons.validator.routines.EmailValidator
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.Argon2
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.dao.Authorizations
import ray.eldath.offgrid.dao.User
import ray.eldath.offgrid.dao.Users
import ray.eldath.offgrid.util.ErrorCode.*
import ray.eldath.offgrid.util.RouteTag
import ray.eldath.offgrid.util.allJson
import java.util.*

class Login(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class LoginRequest(val email: String, val password: String) {
        init {
            require(EmailValidator.getInstance().isValid(email))
        }
    }

    data class LoginResponse(val bearer: String, val expireIn: Long)

    private val expireIn = (BearerSecurity.EXPIRY_MINUTES * 60).toLong()
    private val requestLens = Body.auto<LoginRequest>().toLens()
    private val responseLens = Body.auto<LoginResponse>().toLens()

    private fun handler(): HttpHandler = { req: Request ->
        val json = requestLens(req)
        val email = json.email
        val plainPassword = json.password.toByteArray()

        if (!EmailValidator.getInstance().isValid(email))
            throw INVALID_EMAIL_ADDRESS()

        val user: User = transaction {
            val auth = try {
                (Users innerJoin Authorizations).select { Users.email eq email }.single()
            } catch (e: NoSuchElementException) {
                throw USER_NOT_FOUND()
            }

            auth.let {
                if (!it[Users.emailConfirmed])
                    throw UNCONFIRMED_EMAIL()
                else if (!Argon2.verify(it[Authorizations.hashedPassword].bytes, plainPassword))
                    throw USER_NOT_FOUND()

                User(it[Users.id])
            }
        }

        val bearer = BearerSecurity.authorize(user)
        Response(OK).with(responseLens of LoginResponse(bearer, expireIn))
    }

    override fun compile(): ContractRoute {

        return "/login" meta {
            summary = "Login, use Bearer Authorization"
            tags += RouteTag.User
            tags += RouteTag.Authorization
            allJson()

            returning(OK, responseLens to LoginResponse(UUID.randomUUID().toString(), expireIn))
            exception(INVALID_EMAIL_ADDRESS, UNCONFIRMED_EMAIL, USER_NOT_FOUND)
        } bindContract Method.POST to handler()
    }
}