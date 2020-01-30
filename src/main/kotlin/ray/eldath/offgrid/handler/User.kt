package ray.eldath.offgrid.handler

import org.apache.commons.validator.routines.EmailValidator
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.jooq.exception.NoDataFoundException
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.Argon2
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.component.BearerSecurity.bearerToken
import ray.eldath.offgrid.component.InboundUser
import ray.eldath.offgrid.generated.offgrid.tables.Authorizations
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.util.ErrorCodes.INVALID_EMAIL_ADDRESS
import ray.eldath.offgrid.util.ErrorCodes.UNCONFIRMED_EMAIL
import ray.eldath.offgrid.util.ErrorCodes.USER_NOT_FOUND
import ray.eldath.offgrid.util.RouteTag
import ray.eldath.offgrid.util.allJson
import ray.eldath.offgrid.util.transaction
import java.util.*

class Login(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class LoginRequest(val email: String, val password: String)
    data class LoginResponse(val bearer: String, val expireIn: Long)

    private val expireIn = (BearerSecurity.EXPIRY_MINUTES * 60).toLong()

    private fun handler(): HttpHandler = { req: Request ->
        val json = requestLens(req)
        val email = json.email
        val plainPassword = json.password.toByteArray()

        if (!EmailValidator.getInstance().isValid(email))
            throw INVALID_EMAIL_ADDRESS()

        val inbound = try {
            fetchInboundUser(email)
        } catch (e: NoDataFoundException) {
            throw USER_NOT_FOUND()
        }.apply {
            if (user.isEmailConfirmed == false)
                throw UNCONFIRMED_EMAIL()
            else if (!Argon2.verify(authorization.passwordHashed, plainPassword))
                throw USER_NOT_FOUND()
        }

        val bearer = BearerSecurity.authorize(inbound)
        Response(OK).with(responseLens of LoginResponse(bearer, expireIn))
    }

    override fun compile(): ContractRoute =
        "/login" meta {
            summary = "Login, use Bearer Authorization"
            tags += RouteTag.Authorization
            allJson()

            returning(OK, responseLens to LoginResponse(UUID.randomUUID().toString(), expireIn))
            exception(INVALID_EMAIL_ADDRESS, UNCONFIRMED_EMAIL, USER_NOT_FOUND)
        } bindContract Method.POST to handler()

    companion object {
        val requestLens = Body.auto<LoginRequest>().toLens()
        val responseLens = Body.auto<LoginResponse>().toLens()

        fun fetchInboundUser(email: String) =
            transaction {
                val u = Users.USERS
                val a = Authorizations.AUTHORIZATIONS
                val e = ExtraPermissions.EXTRA_PERMISSIONS

                val (user, auth) = select()
                    .from(u)
                    .innerJoin(a).onKey()
                    .where(u.EMAIL.eq(email))
                    .fetchSingle {
                        Pair(
                            it.into(u).into(User::class.java),
                            it.into(a).into(Authorization::class.java)
                        )
                    }

                val list = select()
                    .from(e)
                    .innerJoin(a).on(e.AUTHORIZATION_ID.eq(a.USER_ID))
                    .fetch { it.into(e).into(ExtraPermission::class.java) }

                InboundUser(user, auth, list)
            }
    }
}

class Logout(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {

    private fun handler(): HttpHandler = { req ->
        BearerSecurity.invalidate(req.bearerToken())
        Response(OK)
    }

    override fun compile(): ContractRoute =
        "/logout" meta {
            summary = "Logout"
            tags += RouteTag.Authorization
            tags += RouteTag.Secure
            security = optionalSecurity
            allJson()

            returning(OK to "given bearer has been invalidate in the cache")
        } bindContract Method.GET to handler()
}