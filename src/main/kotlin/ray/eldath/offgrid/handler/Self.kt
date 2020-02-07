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

class Self(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    private val handler: HttpHandler = { req ->

        credentials(req).run {
            OutboundUser(
                user.id,
                user.username,
                user.email,
                authorization.role.toOutbound(),
                permissions.toOutbound()
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
            security = optionalSecurity

            produces += ContentType.APPLICATION_JSON
            returning(Status.OK, responseLens to OutboundUser.mock)
        } bindContract Method.GET to handler
}

class DeleteSelf(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    private val handler: HttpHandler = { req ->
        BearerSecurity.invalidate(req.safeBearerToken())
        DeleteUser.deleteUser(credentials(req).user.id)

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/self" meta {
            summary = "Delete current user"
            description = "User should be redirected to login page immediately."
            tags += RouteTag.Self
            security = optionalSecurity

            returning(Status.OK to "current logged user has been deleted.")
        } bindContract Method.DELETE to handler
}