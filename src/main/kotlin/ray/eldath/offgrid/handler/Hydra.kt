package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.*
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.http4k.format.Jackson.json
import org.http4k.lens.Query
import org.http4k.lens.string
import org.slf4j.LoggerFactory
import ray.eldath.offgrid.component.ApiException
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.component.UserRegistrationStatus
import ray.eldath.offgrid.core.Core
import ray.eldath.offgrid.util.*
import java.util.concurrent.TimeUnit

object Hydra {
    private val logger = LoggerFactory.getLogger("hydra.okhttp")
    private val HYDRA_HOST = Core.getEnv("OFFGRID_HYDRA_ADMIN_HOST").trimEnd('/')

    private val client by lazy {
        OkHttpClient.Builder().apply {
            connectTimeout(500, TimeUnit.MILLISECONDS)
            callTimeout(2, TimeUnit.SECONDS)
            followRedirects(false)

            addNetworkInterceptor {
                val response = it.proceed(it.request())
                if (!response.isSuccessful) {
                    logger.error("request ${it.request()} failed with response: $response")
                    throw ErrorCodes.commonInternalServerError("HTTP request to hydra failed with: $response")()
                } else {
                    System.err.println("success: $response")
                }
                response

            }
        }.let { OkHttp(client = it.build()) }
    }

    private val loginChallengeLens =
        Query.string().required("login_challenge", "login challenge code provided by Hydra")

    class HydraCheck : ContractHandler {

        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
        data class HydraCheckResponse(val clientName: String, val requestedScope: List<String>)

        private val handler: HttpHandler = { req ->
            val hydra =
                Request(Method.GET, "$HYDRA_HOST/oauth2/auth/requests/login")
                    .query("login_challenge", loginChallengeLens(req))
                    .let(client).let { jacksonObjectMapper().readTree(it.bodyString()) }

            val skip = hydra["skip"].asBoolean()
            if (skip)
                acceptLogin(loginChallengeLens(req), hydra["subject"].asText())
            else Response(Status.OK).with(
                responseLens of HydraCheckResponse(
                    hydra["client"]["client_name"].asText("unknown service"),
                    hydra["requested_scope"].map { it.asText() })
            )
        }

        override fun compile(): ContractRoute =
            "/hydra/check" meta {
                summary = "Check if already logged in"
                description = "If the challenge already logged in, redirection could perform directly. Otherwise " +
                        "username & password should be verified by UI, and then submitted to another API."
                queries += loginChallengeLens
                tags += RouteTag.Hydra

                returning(Status.TEMPORARY_REDIRECT to "user had logged, directly redirect accordingly")
                returning(Status.OK, responseLens to HydraCheckResponse("Grafana", listOf("profile", "grafana")))
            } bindContract Method.GET to handler

        companion object {
            val responseLens = Body.auto<HydraCheckResponse>().toLens()
        }
    }

    class HydraLogin : ContractHandler {
        private val handler: HttpHandler = { req ->
            val challenge = loginChallengeLens(req)
            try {
                val inbound = Login.requestLens(req).let { Login.authenticate(it.email, it.password.toByteArray()) }
                acceptLogin(challenge, inbound.user.email)
            } catch (e: ApiException) {
                rejectLogin(challenge, e.data.run { ErrorCode(code, message, status) })
            }
        }

        override fun compile(): ContractRoute =
            "/hydra/login" meta {
                summary = "Conventional login"
                description = "Login with regular procedure."
                queries += loginChallengeLens
                tags += RouteTag.Hydra

                receiving(Login.requestLens to Login.LoginRequest("alpha.beta@omega.com", "mypassword"))
                returning(Status.TEMPORARY_REDIRECT to "redirect request accordingly")
            } bindContract Method.POST to handler
    }

    private val acceptConsentLens = Body.json().toLens()
    private val json = Jackson

    class HydraConsent : ContractHandler {
        private val challengeLens =
            Query.string().required("consent_challenge", "consent challenge code provided by Hydra")

        private val handler: HttpHandler = { req ->
            val challenge = challengeLens(req)
            val hydra = Request(Method.PUT, "$HYDRA_HOST/oauth2/auth/requests/consent")
                .query("consent_challenge", challenge).also { println(it) }.let(client).also { println(it) }.asJson()

            val email = hydra["subject"].asText()
            val requestedScopes = hydra["requested_scope"].map { it.asText() }

            val permissions =
                requestedScopes.map { OAuthScope.fromId(it) }.requireNoNulls().flatMap { it.permissions.toList() }

            val (user, auth, _) = UserRegistrationStatus.fetchByEmail(email).rightOrThrow.rightOrThrow.requirePermissionOr(
                {
                    reject(
                        uri = "$HYDRA_HOST/oauth2/auth/requests/consent/reject",
                        challenge = challenge,
                        exception = ErrorCodes.permissionDenied(it),
                        queryName = "consent_challenge"
                    )
                }, *permissions.toTypedArray()
            )

            val json = json {
                obj(
                    "grant_scope" to array(requestedScopes.map { string(it) }),
                    "remember" to boolean(true),
                    "remember_for" to number(BearerSecurity.EXPIRY_MINUTES * 60),
                    "session" to obj(
                        "id_token" to obj(
                            "name" to string(user.username),
                            "email" to string(user.email),
                            "email_verified" to boolean(true),
                            "role" to string(if (auth.role == UserRole.Root) "Admin" else "Editor")
                        )
                    )
                )
            }

            Request(Method.PUT, "$HYDRA_HOST/oauth2/auth/requests/consent/accept")
                .query("consent_challenge", challenge)
                .with(acceptConsentLens of json)
                .let(client).redirectAccordingly()
        }

        override fun compile(): ContractRoute =
            "/hydra/consent" meta {
                summary = "Consent by permissions check"
                description =
                    "Currently no designated consent UI should be rendered, this API will just consent by user permission."
                queries += loginChallengeLens
                tags += RouteTag.Hydra

                returning(Status.TEMPORARY_REDIRECT to "redirect request accordingly")
            } bindContract Method.GET to handler
    }

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class HydraRejectRequest(val error: String, val errorDescription: String) {
        companion object {
            val lens = Body.auto<HydraRejectRequest>().toLens()

            fun fromErrorCode(e: ErrorCode) = HydraRejectRequest(e.status.description, e.message)
        }
    }

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class HydraLoginAcceptRequest(
        val subject: String,
        val remember: Boolean = true,
        val rememberFor: Int = BearerSecurity.EXPIRY_MINUTES * 60
    ) {
        companion object {
            val lens = Body.auto<HydraLoginAcceptRequest>().toLens()
        }
    }

    private fun reject(uri: String, challenge: String, exception: ErrorCode, queryName: String = "login_challenge") =
        Request(Method.PUT, uri).query(queryName, challenge)
            .with(HydraRejectRequest.lens of HydraRejectRequest.fromErrorCode(exception))
            .let(client).redirectAccordingly()

    private fun rejectLogin(challenge: String, exception: ErrorCode) =
        reject("$HYDRA_HOST/oauth2/auth/requests/login/reject", challenge, exception)

    private fun acceptLogin(challenge: String, email: String) =
        Request(Method.PUT, "$HYDRA_HOST/oauth2/auth/requests/login/accept")
            .query("login_challenge", challenge)
            .with(HydraLoginAcceptRequest.lens of HydraLoginAcceptRequest(subject = email))
            .let(client).redirectAccordingly()

    private fun Response.redirectAccordingly(key: String = "redirect_to") =
        Response.redirectTo(asJson()[key].asText())
}