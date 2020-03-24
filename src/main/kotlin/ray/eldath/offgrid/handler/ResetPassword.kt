package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.component.Argon2
import ray.eldath.offgrid.generated.offgrid.tables.ResetPasswordApplications
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ResetPasswordApplication
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.model.EmailRequest
import ray.eldath.offgrid.model.UrlToken
import ray.eldath.offgrid.model.UsernamePassword
import ray.eldath.offgrid.util.*
import java.time.Duration
import java.time.LocalDateTime

object ResetPassword {

    class Invoke : ContractHandler {

        data class InvokeRequest(val email: String) : EmailRequest(email)

        private val handler: HttpHandler = { req ->
            val json = requestLens(req)
            json.check()

            transaction {
                val u = Users.USERS
                val rpa = ResetPasswordApplications.RESET_PASSWORD_APPLICATIONS

                val email = json.email
                val user = select()
                    .from(u)
                    .where(u.EMAIL.eq(email))
                    .fetchOptional { it.into(u).into(User::class.java) }
                    .getOrNull()

                val urlToken = ResetPasswordUrlToken.generate(email)

                if (user != null) {
                    newRecord(rpa).apply {
                        this.email = email
                        userId = user.id
                        token = urlToken.token
                        requestTime = LocalDateTime.now()
                    }.insert()

                    sendInvokeEmail(urlToken)
                }
            }
            Response(Status.OK)
        }

        override fun compile(): ContractRoute =
            "/reset_password" meta {
                summary = "Request reset password"
                tags += RouteTag.ResetPassword
                inJson()

                receiving(requestLens to InvokeRequest("alpha@beta.com"))
                returning(
                    Status.OK to "Request processed. Note that this code will be returned " +
                            " no matter whether given user is exist."
                )
            } bindContract Method.POST to handler

        companion object {
            private val requestLens = Body.auto<InvokeRequest>().toLens()
            private val ctx = CoroutineScope(Dispatchers.IO)

            fun sendInvokeEmail(token: ResetPasswordUrlToken) = ctx.launch {
                DirectEmailUtil.sendEmail("[Offgrid] 找回您的密码", "resetpasswordinvoke", token.email) {
                    """
                        您好，
                        
                        我们收到了有关本邮箱关联的账户的密码重置请求。若这一请求确实与您有关，请访问以下链接以重置您的密码：
                        
                        ${token.url}
                        
                        该链接 ${TOKEN_EXPIRY_DURATION.toMinutes()} 分钟内有效，请尽快完成密码重置。
                        
                        Offgrid.
                        
                        
                        
                        
                        
                        您收到这封邮件是因为有人提交了与本邮箱关联的密码重置请求。若这一请求与您无关，请忽略此邮件。
                        
                        
                        此邮件由 Offgrid 系统自动发出，请勿直接回复。若有更多问题请联系您组织的 Offgrid 管理员。
                    """.trimIndent()
                }
            }
        }
    }

    private fun verifyToken(token: ResetPasswordUrlToken): ResetPasswordApplication =
        transaction {
            val rpa = ResetPasswordApplications.RESET_PASSWORD_APPLICATIONS

            select()
                .from(rpa)
                .where(rpa.TOKEN.eq(token.token))
                .and(rpa.EMAIL.eq(token.email))
                .fetchOptional { it.into(rpa).into(ResetPasswordApplication::class.java) }
                .orElseThrow { ErrorCodes.TOKEN_NOT_FOUND() }
                .also {
                    if (it.requestTime.plus(TOKEN_EXPIRY_DURATION).isBefore(LocalDateTime.now()))
                        throw ErrorCodes.TOKEN_EXPIRED()
                }
        }

    class Verify : ContractHandler {

        private fun handler(urlToken: String): HttpHandler = {
            verifyToken(ResetPasswordUrlToken.parse(urlToken))

            Response(Status.OK)
        }

        override fun compile(): ContractRoute =
            "/reset_password" / inboundTokenPath meta {
                summary = "Verify the reset URL token"
                tags += RouteTag.ResetPassword

                exception(ErrorCodes.TOKEN_NOT_FOUND, ErrorCodes.TOKEN_EXPIRED)
                returning(Status.OK to "given URL token is valid")
            } bindContract Method.GET to ::handler
    }

    class Submit : ContractHandler {

        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
        data class SubmitRequest(val newPassword: String)

        private fun handler(urlToken: String): HttpHandler = {
            val reset = verifyToken(ResetPasswordUrlToken.parse(urlToken))

            val (password) = requestLens(it)
            UsernamePassword.checkPassword(password)

            transaction {
                val u = Users.USERS
                val rpa = ResetPasswordApplications.RESET_PASSWORD_APPLICATIONS

                update(u)
                    .set(u.HASHED_PASSWORD, Argon2.hash(password.toByteArray()))
                    .where(u.ID.eq(reset.userId)).execute()

                deleteFrom(rpa)
                    .where(rpa.USER_ID.eq(reset.userId)).execute()
            }

            Response(Status.OK)
        }

        override fun compile(): ContractRoute =
            "/reset_password" / inboundTokenPath meta {
                summary = "Submit the new password"
                description = "Still, the URL token will be verified first."
                tags += RouteTag.ResetPassword
                inJson()

                exception(ErrorCodes.TOKEN_NOT_FOUND, ErrorCodes.TOKEN_EXPIRED)
                receiving(requestLens to SubmitRequest("12345678abc"))
                returning(Status.OK to "new password has been successfully set")
            } bindContract Method.POST to ::handler

        companion object {
            private val requestLens = Body.auto<SubmitRequest>().toLens()
        }
    }

    private val TOKEN_EXPIRY_DURATION = Duration.ofMinutes(30)
    private val inboundTokenPath =
        Path.of("urlToken", "generated URL token, contained in the link sent in reset confirmation email")
}

class ResetPasswordUrlToken(override val email: String, override val token: String) :
    UrlToken(email, token, "reset_password") {

    companion object {
        fun parse(token: String) = UrlToken.parse(token).let { ResetPasswordUrlToken(it.email, it.token) }
        fun generate(email: String) = ResetPasswordUrlToken(email, generateToken())
    }
}