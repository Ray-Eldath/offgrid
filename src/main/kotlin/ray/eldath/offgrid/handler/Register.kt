package ray.eldath.offgrid.handler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.http4k.contract.ContractRoute
import org.http4k.contract.RouteMetaDsl
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.Argon2
import ray.eldath.offgrid.component.UserRegistrationStatus
import ray.eldath.offgrid.component.UserRegistrationStatus.*
import ray.eldath.offgrid.generated.offgrid.tables.UserApplications
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.model.EmailRequest
import ray.eldath.offgrid.model.UrlToken
import ray.eldath.offgrid.model.UsernamePassword
import ray.eldath.offgrid.util.DirectEmailUtil
import ray.eldath.offgrid.util.ErrorCodes
import ray.eldath.offgrid.util.ErrorCodes.TOKEN_EXPIRED
import ray.eldath.offgrid.util.ErrorCodes.TOKEN_NOT_FOUND
import ray.eldath.offgrid.util.ErrorCodes.USER_ALREADY_REGISTERED
import ray.eldath.offgrid.util.ErrorCodes.USER_NOT_FOUND
import ray.eldath.offgrid.util.RouteTag
import ray.eldath.offgrid.util.transaction
import java.time.Duration
import java.time.LocalDateTime

class Register : ContractHandler {
    data class RegisterRequest(val email: String) : EmailRequest(email)

    private val handler: HttpHandler = { req: Request ->
        val email = requestLens(req).also { it.check() }.email
        val either = UserRegistrationStatus.fetchByEmail(email)
        if (either.notHaveLeft)
            throw USER_ALREADY_REGISTERED()
        val status = either.left
        when (status) {
            APPLICATION_PENDING -> throw ErrorCodes.APPLICATION_PENDING()
            APPLICATION_REJECTED -> throw ErrorCodes.APPLICATION_REJECTED()
            UNCONFIRMED, NOT_FOUND -> Unit // need to handle
        }

        val ua = UserApplications.USER_APPLICATIONS

        val urlToken = ConfirmEmail.ConfirmUrlToken.generate(email)
        if (status == UNCONFIRMED) {
            either.rightOrThrow.leftOrThrow.let { sendConfirmEmail(urlToken) }

            transaction {
                update(ua)
                    .set(ua.LAST_REQUEST_TOKEN_TIME, LocalDateTime.now())
                    .set(ua.EMAIL_CONFIRMATION_TOKEN, urlToken.token)
                    .where(ua.EMAIL.eq(email))
                    .execute()
            }
        } else if (status == NOT_FOUND) {
            sendConfirmEmail(urlToken)

            transaction {
                val record = newRecord(ua).apply {
                    this.email = email
                    isEmailConfirmed = false
                    emailConfirmationToken = urlToken.token
                    lastRequestTokenTime = LocalDateTime.now()
                }
                record.store()
            }
        }

        Response(OK)
    }

    override fun compile(): ContractRoute =
        "/register" meta {
            summary = "Register"
            description = "Note that only email address is needed."
            tags += RouteTag.Registration
            consumes += ContentType.APPLICATION_JSON

            exception(ErrorCodes.APPLICATION_REJECTED, ErrorCodes.APPLICATION_PENDING)
            receiving(requestLens to RegisterRequest("alpha.beta@omega.com"))
            returning(OK to "confirm email has been sent, or resent successfully")
        } bindContract Method.POST to handler

    companion object {
        private val ctx = CoroutineScope(Dispatchers.IO)
        val requestLens = Body.auto<RegisterRequest>().toLens()

        val TOKEN_EXPIRY_DURATION: Duration = Duration.ofHours(2)

        fun sendConfirmEmail(token: ConfirmEmail.ConfirmUrlToken) = ctx.launch {
            DirectEmailUtil.sendEmail("[Offgrid] 注册确认：验证您的邮箱", "emailconfirm", token.email) {
                """
                    您好，
                    
                    感谢您注册 Offgrid！请访问下列链接以验证您的邮箱：
                    
                    ${token.url}
                    
                    该链接 ${TOKEN_EXPIRY_DURATION.toHours()} 小时内有效，请尽快完成注册确认。
                    
                    Offgrid.
                    
                    
                    
                    
                    
                    您收到这封邮件是因为有人使用本邮箱注册了 Offgrid。若这一注册与您无关，请忽略此邮件。
                    
                    
                    此邮件由 Offgrid 系统自动发出，请勿直接回复。若有更多问题请联系您组织的 Offgrid 管理员。
                """.trimIndent()
            }
        }
    }
}

object ConfirmEmail {

    private fun validateUrlToken(urlToken: ConfirmUrlToken): UserApplication {
        val status = UserRegistrationStatus.fetchByEmail(urlToken.email)
        if (status.notHaveLeft)
            throw USER_ALREADY_REGISTERED()
        when (status.leftOrThrow) {
            NOT_FOUND -> throw USER_NOT_FOUND()
            APPLICATION_PENDING -> throw ErrorCodes.APPLICATION_PENDING()
            APPLICATION_REJECTED -> throw ErrorCodes.APPLICATION_REJECTED()
            UNCONFIRMED -> Unit
        }
        val application = status.rightOrThrow.leftOrThrow
        val time = application.lastRequestTokenTime

        if (application.emailConfirmationToken != urlToken.token)
            throw TOKEN_NOT_FOUND()
        else if (time.plus(Register.TOKEN_EXPIRY_DURATION).isBefore(LocalDateTime.now()))
            throw TOKEN_EXPIRED()
        return application
    }

    class SubmitUserApplication : ContractHandler {

        private val requestLens = Body.auto<UsernamePassword>().toLens()

        private fun handler(inboundToken: String) = { req: Request ->
            val json = requestLens(req).also { it.check() }
            val plainPassword = json.password.toByteArray()
            val application = validateUrlToken(ConfirmUrlToken.parse(inboundToken))

            transaction {
                val ua = UserApplications.USER_APPLICATIONS

                update(ua)
                    .set(ua.IS_EMAIL_CONFIRMED, true)
                    .set(ua.USERNAME, json.username)
                    .set(ua.HASHED_PASSWORD, Argon2.hash(plainPassword))
                    .where(ua.EMAIL.eq(application.email))
                    .execute()
            }

            Response(OK)
        }

        override fun compile(): ContractRoute =
            "/confirm" / inboundTokenPath meta {
                summary = "Confirm the registration"
                description =
                    "After the token validated, submit the username & password requested from user. After this, " +
                            "register application will be ready for admission."
                tags += RouteTag.Registration
                consumes += ContentType.APPLICATION_JSON

                injectExceptions()
                receiving(requestLens to UsernamePassword("Ray Eldath", "mypassword"))
                returning(OK to "intact register application is submitted successfully")
            } bindContract Method.POST to ::handler
    }

    class ValidateUrlToken : ContractHandler {

        private fun handler(inboundToken: String) = { _: Request ->
            validateUrlToken(ConfirmUrlToken.parse(inboundToken))

            Response(OK)
        }

        override fun compile(): ContractRoute =
            "/confirm" / inboundTokenPath meta {
                summary = "Validate given URL token"
                description = "The truthy and the expire of the token will be validate."
                tags += RouteTag.Registration

                injectExceptions()
                returning(OK to "given URL token is valid")
            } bindContract Method.GET to ::handler
    }

    private val inboundTokenPath = Path.of("urlToken", "URL token, same as the link sent in confirmation email.")
    private fun RouteMetaDsl.injectExceptions() = exception(
        USER_NOT_FOUND,
        ErrorCodes.APPLICATION_REJECTED,
        ErrorCodes.APPLICATION_PENDING,
        TOKEN_EXPIRED,
        TOKEN_NOT_FOUND
    )

    data class ConfirmUrlToken(override val email: String, override val token: String) :
        UrlToken(email, token, "confirm") {

        companion object {
            fun parse(token: String): ConfirmUrlToken =
                UrlToken.parse(token).let { ConfirmUrlToken(it.email, it.token) }

            fun generate(email: String) = ConfirmUrlToken(email, generateToken())
        }
    }
}