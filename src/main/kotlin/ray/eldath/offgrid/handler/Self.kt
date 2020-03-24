package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.component.BearerSecurity.safeBearerToken
import ray.eldath.offgrid.model.OutboundUser
import ray.eldath.offgrid.model.toOutbound
import ray.eldath.offgrid.util.RouteTag

class Self(credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {

    private val handler: HttpHandler = { req ->

        credentials(req).run {
            OutboundUser(
                user.id,
                user.state.id,
                user.username,
                user.email,
                user.role.toOutbound(),
                permissions = permissions.toOutbound(),
                lastLoginTime = user.lastLoginTime,
                registerTime = user.registerTime
            )
        }.let { Response(Status.OK).with(responseLens of it) }
    }

    companion object {
        private val responseLens = Body.auto<OutboundUser>().toLens()
    }

    override fun compile(): ContractRoute =
        "/self" meta {
            summary = "Get current user's information"
            tags += RouteTag.Self
            security = configuredSecurity

            produces += ContentType.APPLICATION_JSON
            returning(Status.OK, responseLens to OutboundUser.mock)
        } bindContract Method.GET to handler
}

class DeleteSelf(credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {

    private val handler: HttpHandler = { req ->
        val self = credentials(req)
        BearerSecurity.invalidate(req.safeBearerToken())
        DeleteUser.deleteUser(self.user.id)

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/self" meta {
            summary = "Delete current user"
            description = "User should be redirected to login page immediately."
            tags += RouteTag.Self
            security = configuredSecurity

            returning(Status.OK to "current logged user has been deleted.")
        } bindContract Method.DELETE to handler
}