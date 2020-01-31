package ray.eldath.offgrid.handler

import com.aliyuncs.dm.HangZhouDmClient
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest
import com.aliyuncs.exceptions.ClientException
import com.aliyuncs.exceptions.ServerException
import com.aliyuncs.http.MethodType
import org.apache.commons.validator.routines.EmailValidator
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.slf4j.LoggerFactory
import ray.eldath.offgrid.component.*
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.BearerSecurity.bearerToken
import ray.eldath.offgrid.component.UserRegistrationStatus.*
import ray.eldath.offgrid.core.Core.getEnv
import ray.eldath.offgrid.generated.offgrid.tables.UserApplications
import ray.eldath.offgrid.util.*
import ray.eldath.offgrid.util.ErrorCodes.INVALID_EMAIL_ADDRESS
import ray.eldath.offgrid.util.ErrorCodes.UNCONFIRMED_EMAIL
import ray.eldath.offgrid.util.ErrorCodes.USER_NOT_FOUND
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
            }) either either.right
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
        val status = either.leftOrThrow
        when (status) {
            APPLICATION_PENDING -> throw ErrorCodes.APPLICATION_PENDING()
            APPLICATION_REJECTED -> throw ErrorCodes.APPLICATION_REJECTED()
            UNCONFIRMED, NOT_FOUND -> Unit // need to handle
        }

        val ua = UserApplications.USER_APPLICATIONS
        if (status == UNCONFIRMED) {
            either.rightOrThrow.leftOrThrow.let {
                sendConfirmEmail(email, ConfirmEmail.buildConfirmUrl(it.email, it.emailConfirmationToken))
            }

            transaction {
                update(ua)
                    .set(ua.LAST_REQUEST_TOKEN_TIME, LocalDateTime.now())
                    .where(ua.EMAIL.eq(email))
                    .execute()
            }
        } else if (status == NOT_FOUND) {
            transaction {
                val token = ConfirmEmail.generateConfirmToken()
                sendConfirmEmail(email, token)

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
            allJson()

            exception(ErrorCodes.APPLICATION_REJECTED, ErrorCodes.APPLICATION_PENDING)
            returning(OK to "confirm email has been sent, or resent successfully")
        } bindContract Method.POST to handler

    companion object {
        private val logger = LoggerFactory.getLogger(Register::class.java)
        val requestLens = Body.auto<RegisterRequest>().toLens()

        const val TOKEN_EXPIRY_HOURS = 2

        private val aliyunClient =
            HangZhouDmClient(
                getEnv("OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_ID"),
                getEnv("OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_SECRET")
            ).get()

        fun sendConfirmEmail(email: String, confirmUrl: String) {

            fun warn(e: ClientException, type: String = "ClientException") =
                logger.warn(
                    "AliyunDirectMail: $type(errCode: ${e.errCode}) thrown when sendConfirmEmail to email address $email",
                    e
                )

            try {
                SingleSendMailRequest().apply {
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
                    
                    该链接 $TOKEN_EXPIRY_HOURS 小时内有效，请尽快完成注册确认。
                    
                    Offgrid.
                    
                    
                    
                    
                    
                    您收到这封邮件是因为有人使用本邮箱注册了 Offgrid。若这一注册与您无关，请忽略此邮件。
                    
                    此邮件由 Offgrid 系统自动发出，请勿直接回复。若有更多问题请联系您组织的 Offgrid 管理员。
                """.trimIndent()
                }.let { aliyunClient.doAction(it) }

            } catch (e: ServerException) {
                warn(e, "ServerException")
                throw e
            } catch (e: ClientException) {
                warn(e)
                throw e
            }
        }
    }
}

class ConfirmEmail(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {
    override fun compile(): ContractRoute {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    data class ConfirmUrlToken(val email: String, val token: String)

    companion object {
        fun generateConfirmToken() = UUID.randomUUID().toString()

        fun buildConfirmUrl(email: String, token: String): String {
            val urlToken = Jackson.asJsonString(ConfirmUrlToken(email, token)).base64Url()

            return "${getEnv("OFFGRID_HOST")}/confirm/$urlToken"
        }

        fun parseConfirmUrlToken(token: String): ConfirmUrlToken =
            Jackson.asA(token.decodeBase64Url(), ConfirmUrlToken::class)
    }
}

open class EmailRequest(val emailAddress: String) {
    init {
        if (EmailValidator.getInstance().isValid(emailAddress))
            throw INVALID_EMAIL_ADDRESS()
    }
}