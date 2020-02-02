package ray.eldath.offgrid.handler

import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest
import com.aliyuncs.exceptions.ClientException
import com.aliyuncs.exceptions.ServerException
import com.aliyuncs.http.MethodType
import com.aliyuncs.profile.DefaultProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.validator.routines.EmailValidator
import org.http4k.contract.ContractRoute
import org.http4k.contract.RouteMetaDsl
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.slf4j.LoggerFactory
import ray.eldath.offgrid.component.*
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.BearerSecurity.bearerToken
import ray.eldath.offgrid.component.UserRegistrationStatus.*
import ray.eldath.offgrid.core.Core.getEnv
import ray.eldath.offgrid.generated.offgrid.tables.UserApplications
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.util.*
import ray.eldath.offgrid.util.ErrorCodes.CONFIRM_TOKEN_EXPIRED
import ray.eldath.offgrid.util.ErrorCodes.CONFIRM_TOKEN_NOT_FOUND
import ray.eldath.offgrid.util.ErrorCodes.INVALID_EMAIL_ADDRESS
import ray.eldath.offgrid.util.ErrorCodes.InvalidRegisterSubmission
import ray.eldath.offgrid.util.ErrorCodes.UNCONFIRMED_EMAIL
import ray.eldath.offgrid.util.ErrorCodes.USER_ALREADY_REGISTERED
import ray.eldath.offgrid.util.ErrorCodes.USER_NOT_FOUND
import ray.eldath.offgrid.util.ErrorCodes.sendEmailFailed
import java.time.Duration
import java.time.LocalDateTime
import java.util.*


class Login(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class LoginRequest(val email: String, val password: String) : EmailRequest(email)
    data class LoginResponse(val bearer: String, val expireIn: Long)

    private val expireIn = (BearerSecurity.EXPIRY_MINUTES * 60).toLong()

    private val handler: HttpHandler = { req: Request ->
        val json = requestLens(req)
        val email = json.emailAddress
        val plainPassword = json.password.toByteArray()

        if (!EmailValidator.getInstance().isValid(email))
            throw INVALID_EMAIL_ADDRESS()

        val either = runState(UserRegistrationStatus.fetchByEmail(email), plainPassword)
        val inbound =
            if (either.haveLeft)
                throw either.leftOrThrow()
            else
                either.rightOrThrow.rightOrThrow


        val bearer = BearerSecurity.authorize(inbound)
        Response(OK).with(responseLens of LoginResponse(bearer, expireIn))
    }

    override fun compile(): ContractRoute =
        "/login" meta {
            summary = "Login, use Bearer Authorization"
            tags += RouteTag.Authorization
            allJson()

            receiving(requestLens)
            returning(OK, responseLens to LoginResponse(UUID.randomUUID().toString(), expireIn))
            exception(
                INVALID_EMAIL_ADDRESS,
                UNCONFIRMED_EMAIL,
                USER_NOT_FOUND,
                ErrorCodes.APPLICATION_PENDING,
                ErrorCodes.APPLICATION_REJECTED
            )
        } bindContract Method.POST to handler

    companion object {
        val requestLens = Body.auto<LoginRequest>().toLens()
        val responseLens = Body.auto<LoginResponse>().toLens()

        fun <L : UserRegistrationStatus, R : ApplicationOrInbound> runState(
            either: Either<L, R>,
            plainPassword: ByteArray
        ): Either<ErrorCode, ApplicationOrInbound> =
            (if (either.haveLeft)
                when (either.leftOrThrow) {
                    UNCONFIRMED -> UNCONFIRMED_EMAIL
                    APPLICATION_PENDING -> ErrorCodes.APPLICATION_PENDING
                    APPLICATION_REJECTED -> ErrorCodes.APPLICATION_REJECTED
                    else -> USER_NOT_FOUND
                }
            else {
                val i: InboundUser = either.rightOrThrow.rightOrThrow
                if (!Argon2.verify(i.authorization.hashedPassword, plainPassword))
                    USER_NOT_FOUND
                else null
            }) or either.right
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

            returning(OK to "given bearer has been invalidated in the cache")
        } bindContract Method.GET to handler()
}

class Register(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class RegisterRequest(val email: String) : EmailRequest(email)

    private val handler: HttpHandler = { req: Request ->
        val email = requestLens(req).email
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
        val ctx = CoroutineScope(Dispatchers.IO)
        fun sendEmail(token: String) =
            ctx.launch {
                sendConfirmEmail(email, ConfirmEmail.ConfirmUrlToken.buildUrl(email, token))
            }

        if (status == UNCONFIRMED) {
            either.rightOrThrow.leftOrThrow.let { sendEmail(it.emailConfirmationToken) }

            transaction {
                update(ua)
                    .set(ua.LAST_REQUEST_TOKEN_TIME, LocalDateTime.now())
                    .where(ua.EMAIL.eq(email))
                    .execute()
            }
        } else if (status == NOT_FOUND) {
            val token = ConfirmEmail.ConfirmUrlToken.generateToken()
            sendEmail(token)

            transaction {
                val record = newRecord(ua).apply {
                    this.email = email
                    isEmailConfirmed = false
                    emailConfirmationToken = token
                    lastRequestTokenTime = LocalDateTime.now()
                }
                record.store()
            }
        }

        Response(OK)
    }

    override fun compile(): ContractRoute =
        "/register" meta {
            summary = "Register, only email address is needed"
            tags += RouteTag.User
            tags += RouteTag.Authorization
            consumes += ContentType.APPLICATION_JSON

            exception(ErrorCodes.APPLICATION_REJECTED, ErrorCodes.APPLICATION_PENDING)
            returning(OK to "confirm email has been sent, or resent successfully")
        } bindContract Method.POST to handler

    companion object {
        private val logger = LoggerFactory.getLogger(Register::class.java)
        val requestLens = Body.auto<RegisterRequest>().toLens()

        val TOKEN_EXPIRY_DURATION: Duration = Duration.ofHours(2)

        private val aliyunClient by lazy {
            DefaultProfile.getProfile(
                "cn-hangzhou",
                getEnv("OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_ID"),
                getEnv("OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_SECRET") // require permission: AliyunDirectMailFullAccess...... :-(
            ).let {
                DefaultAcsClient(it)
            }
        }

        suspend fun sendConfirmEmail(email: String, confirmUrl: String) = withContext(Dispatchers.IO) {

            fun warn(e: ClientException, type: String = "ClientException"): Unit =
                "AliyunDirectMail: $type(errCode: ${e.errCode}) thrown when sendConfirmEmail to email address $email".let {
                    logger.warn(it, e)

                    throw sendEmailFailed(email, it + "\n $type: ${e.json()}")()
                }

            try {
                val resp = SingleSendMailRequest().apply {
                    accountName = "no-reply@qvq.ink"
                    fromAlias = "no-reply"
                    addressType = 1
                    tagName = "emailconfirm"
                    replyToAddress = true
                    sysMethod = MethodType.POST
                    clickTrace = "0";
                    toAddress = email
                    subject = "[Offgrid] 注册确认：验证您的邮箱"
                    textBody = """
                    您好，
                    
                    感谢您注册 Offgrid！请访问下列链接以验证您的邮箱：
                    
                    $confirmUrl
                    
                    该链接 ${TOKEN_EXPIRY_DURATION.toHours()} 小时内有效，请尽快完成注册确认。
                    
                    Offgrid.
                    
                    
                    
                    
                    
                    您收到这封邮件是因为有人使用本邮箱注册了 Offgrid。若这一注册与您无关，请忽略此邮件。
                    
                    此邮件由 Offgrid 系统自动发出，请勿直接回复。若有更多问题请联系您组织的 Offgrid 管理员。
                """.trimIndent()
                }.let { aliyunClient.doAction(it) }
                if (!resp.isSuccess)
                    ("AliyunDirectMail: unsuccessful send attempt to email address $email with response: \n" +
                            "(${resp.status}) \n ${resp.httpContentString}")
                        .let {
                            logger.warn(it)
                            throw sendEmailFailed(email, it)()
                        }

            } catch (e: ServerException) {
                warn(e, "ServerException")
            } catch (e: ClientException) {
                warn(e)
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
            throw CONFIRM_TOKEN_NOT_FOUND()
        else if (time.plus(Register.TOKEN_EXPIRY_DURATION).isBefore(LocalDateTime.now()))
            throw CONFIRM_TOKEN_EXPIRED()
        return application
    }

    class SubmitUserApplication(credentials: Credentials, optionalSecurity: Security) :
        ContractHandler(credentials, optionalSecurity) {

        data class UserApplicationSubmission(val username: String, val password: String) {
            companion object {
                private const val MAX_USERNAME_LENGTH = 16
                private const val MAX_PASSWORD_LENGTH = 18
                private const val MIN_PASSWORD_LENGTH = 6
            }

            fun check() {
                val uLength = username.length
                val pLength = password.length
                when {
                    uLength > MAX_USERNAME_LENGTH -> throw InvalidRegisterSubmission.USERNAME_TOO_LONG()
                    pLength <= MIN_PASSWORD_LENGTH -> throw InvalidRegisterSubmission.PASSWORD_TOO_SHORT()
                    pLength > MAX_PASSWORD_LENGTH -> throw InvalidRegisterSubmission.PASSWORD_TOO_LONG()
                }
            }
        }

        private val requestLens = Body.auto<UserApplicationSubmission>().toLens()

        private fun handler(inboundToken: String) = { req: Request ->
            val json = requestLens(req)
            json.check()
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
                summary =
                    "After the token validated, submit the username & password requested from user. After this, " +
                            "register application will be ready for admission"
                tags += RouteTag.Registration
                consumes += ContentType.APPLICATION_JSON

                injectExceptions()
                receiving(requestLens)
                returning(OK to "intact register application is submitted successfully")
            } bindContract Method.POST to ::handler
    }

    class ValidateUrlToken(credentials: Credentials, optionalSecurity: Security) :
        ContractHandler(credentials, optionalSecurity) {

        private fun handler(inboundToken: String) = { _: Request ->
            validateUrlToken(ConfirmUrlToken.parse(inboundToken))

            Response(OK)
        }

        override fun compile(): ContractRoute =
            "/confirm" / inboundTokenPath meta {
                summary = "Validate the truthy as well as the expire of given URL token"
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
        CONFIRM_TOKEN_EXPIRED,
        CONFIRM_TOKEN_NOT_FOUND
    )

    data class ConfirmUrlToken(val email: String, val token: String) : EmailRequest(email) {
        companion object {
            fun generateToken() = UUID.randomUUID().toString()

            fun buildUrl(email: String, token: String): String {
                val urlToken = Jackson.asJsonString(ConfirmUrlToken(email, token)).base64Url()

                return "${getEnv("OFFGRID_HOST")}/confirm/$urlToken"
            }

            fun parse(token: String): ConfirmUrlToken =
                Jackson.asA(token.decodeBase64Url(), ConfirmUrlToken::class)
        }
    }
}

open class EmailRequest(val emailAddress: String) {
    init {
        if (!EmailValidator.getInstance().isValid(emailAddress))
            throw INVALID_EMAIL_ADDRESS()
    }
}