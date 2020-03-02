package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.client.OkHttp
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Query
import org.http4k.lens.string
import ray.eldath.offgrid.component.ApiException
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.core.Core
import ray.eldath.offgrid.util.RouteTag
import ray.eldath.offgrid.util.asJson
import ray.eldath.offgrid.util.redirectTo

object Hydra {
    private val client = OkHttp()
    private val hydraHost = Core.getEnv("OFFGRID_HYDRA_HOST").trimEnd('/')
    private val challengeLens = Query.string().required("login_challenge", "challenge code provided by Hydra")

    class HydraCheck : ContractHandler {

        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
        data class HydraCheckResponse(val clientName: String, val requestedScope: List<String>)

        private val handler: HttpHandler = { req ->
            val hydra =
                Request(Method.GET, "$hydraHost/oauth2/auth/requests/login")
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
                returning(Status.OK, responseLens to HydraCheckResponse("Grafana", listOf("profile", "system_metrics")))
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
                rejectLogin(challenge, e)
            }
        }

        override fun compile(): ContractRoute =
            "/hydra/login" meta {
                summary = "Conventional login"
                description = "Login with regular procedure."
                queries += challengeLens
                tags += RouteTag.Hydra

                receiving(Login.requestLens)
                returning(Status.TEMPORARY_REDIRECT to "redirect accordingly")
            } bindContract Method.POST to handler
    }

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class HydraRejectRequest(val error: String, val errorDescription: String) {
        companion object {
            val lens = Body.auto<HydraRejectRequest>().toLens()

            fun fromApiException(e: ApiException) = HydraRejectRequest(e.data.status.description, e.data.message)
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

    private fun reject(uri: String, challenge: String, exception: ApiException) =
        Request(Method.PUT, uri).query("login_challenge", challenge)
            .with(HydraRejectRequest.lens of HydraRejectRequest.fromApiException(exception)).let(client)
            .let { Response.redirectTo(it.asJson()["redirect_to"].asText()) }

    private fun rejectLogin(challenge: String, exception: ApiException) =
        reject("$hydraHost/oauth2/auth/requests/login/reject", challenge, exception)

    private fun acceptLogin(challenge: String, subject: String) =
        Request(Method.PUT, "$hydraHost/oauth2/auth/requests/login/accept")
            .query("login_challenge", challenge)
            .with(HydraLoginAcceptRequest.lens of HydraLoginAcceptRequest(subject)).let(client)
            .let { Response.redirectTo(it.asJson()["redirect_to"].asText()) }
}