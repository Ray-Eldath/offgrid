package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.RouteTag
import ray.eldath.offgrid.util.UserRole

class Self(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    data class SelfResponse(
        val id: Int,
        val username: String,
        val email: String,
        val role: ExchangeRole,
        val permissions: List<ExchangePermission>? = null
    )

    private fun handler(): HttpHandler = { req ->

        credentials(req).run {
            SelfResponse(user.id, user.username, user.email, authorization.role.toExchangeable())
        }.let { Response(Status.OK).with(responseLens of it) }
    }

    companion object {
        private val responseLens = Body.auto<SelfResponse>().toLens()
    }

    override fun compile(): ContractRoute =
        "/self" meta {
            summary = "Get current user's information"
            tags += RouteTag.Self
            security = optionalSecurity

            produces += ContentType.APPLICATION_JSON
            returning(
                Status.OK, responseLens to SelfResponse(
                    330213, "Ray Eldath", "alpha@beta.omega", UserRole.Root.toExchangeable(),
                    listOf(Permission.ComputationResult.toExchangeable(true))
                )
            )
        } bindContract Method.GET to handler()
}