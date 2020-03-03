package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.client.OkHttp
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.*
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.http4k.format.Jackson.json
import org.http4k.lens.Query
import org.http4k.lens.string
import ray.eldath.offgrid.component.ApiException
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.component.UserRegistrationStatus
import ray.eldath.offgrid.core.Core
import ray.eldath.offgrid.util.*

object Hydra {
    private val HYDRA_HOST = Core.getEnv("OFFGRID_HYDRA_HOST").trimEnd('/')

    private val client = OkHttp()
    private val challengeLens = Query.string().required("login_challenge", "challenge code provided by Hydra")

    class HydraCheck : ContractHandler {

        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
        data class HydraCheckResponse(val clientName: String, val requestedScope: List<String>)

        private val handler: HttpHandler = { req ->
            val hydra =
                Request(Method.GET, "$HYDRA_HOST/oauth2/auth/requests/login")
                    .query("login_challenge", challengeLens(req))
                    .let(client).let { jacksonObjectMapper().readTree(it.bodyString()) }

            val skip = hydra["skip"].asBoolean()
            if (skip)
                acceptLogin(challengeLens(req), hydra["subject"].asText())
            else Response(Status.OK).with(
                responseLens of HydraCheckResponse(
                    hydra["client"]["name"].asText("unknown service"),
                    hydra["requested_scope"].map { it.asText() })
            )
        }

        override fun compile(): ContractRoute =
            "/hydra/check" meta {
                summary = "Check if already logged in"
                description = "If the challenge already logged in, redirection could perform directly. Otherwise " +
                        "username & password should be verified by UI, and then submitted to another API."
                queries += challengeLens
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
            val challenge = challengeLens(req)
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
                queries += challengeLens
                tags += RouteTag.Hydra

                receiving(Login.requestLens to Login.LoginRequest("alpha.beta@omega.com", "mypassword"))
                returning(Status.TEMPORARY_REDIRECT to "redirect request accordingly")
            } bindContract Method.POST to handler
    }

    private val acceptConsentLens = Body.json().toLens()
    private val json = Jackson

    class HydraConsent : ContractHandler {

        private val handler: HttpHandler = { req ->
            val challenge = challengeLens(req)
            val hydra = Request(Method.PUT, "$HYDRA_HOST/oauth2/auth/requests/consent")
                .query("login_challenge", challenge).let(client).asJson()

            val email = hydra["subject"].asText()
            val requestedScopes = hydra["requested_scope"].map { it.asText() }

            val permissions =
                requestedScopes.map { OAuthScope.fromId(it) }.requireNoNulls().flatMap { it.permissions.toList() }

            val (user, auth, _) = UserRegistrationStatus.fetchByEmail(email).rightOrThrow.rightOrThrow.requirePermissionOr(
                {
                    reject(
                        uri = "$HYDRA_HOST/oauth2/auth/requests/consent/reject",
                        challenge = challenge,
                        exception = ErrorCodes.permissionDenied(it)
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
                .query("login_challenge", challenge)
                .with(acceptConsentLens of json)
                .let(client).redirectAccordingly()
        }

        override fun compile(): ContractRoute =
            "/hydra/consent" meta {
                summary = "Consent by permissions check"
                description =
                    "Currently no designated consent UI should be rendered, this API will just consent by user permission."
                queries += challengeLens
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

    private fun reject(uri: String, challenge: String, exception: ErrorCode) =
        Request(Method.PUT, uri).query("login_challenge", challenge)
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