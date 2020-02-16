package ray.eldath.offgrid.handler

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import ray.eldath.offgrid.model.OutboundPermission
import ray.eldath.offgrid.model.OutboundRole
import ray.eldath.offgrid.model.toOutbound
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.RouteTag
import ray.eldath.offgrid.util.UserRole
import ray.eldath.offgrid.util.outJson

class MetaUserRoles(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class RolesEntry(val role: OutboundRole, val defaultPermissions: List<OutboundPermission>)

    data class RolesResponse(val permissions: List<OutboundTreePermission>?, val roles: List<RolesEntry>)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class OutboundTreePermission(
        val id: String,
        val name: String,
        val children: List<OutboundTreePermission>? = null
    )

    override fun compile(): ContractRoute =
        "/meta/roles" meta {
            summary = "Get all permissions and roles"
            tags += RouteTag.Meta
            security = optionalSecurity
            outJson()

            returning("requested data" to response)
        } bindContract Method.GET to handler

    companion object {
        private val handler: HttpHandler = { response }

        private val response by lazy {
            fun Permission.toTree(): OutboundTreePermission {
                return if (childrenPermissions.isNullOrEmpty())
                    OutboundTreePermission(id, name)
                else
                    OutboundTreePermission(id, name,
                        childrenPermissions.filterNotNull().map { it.toTree() })
            }

            Response(Status.OK).with(
                Body.auto<RolesResponse>().toLens() of RolesResponse(
                    permissions = Permission.Root.toTree().children,
                    roles = UserRole.values().map {
                        RolesEntry(
                            OutboundRole(it.id, it.name),
                            it.defaultPermissions.toOutbound()
                        )
                    }
                )
            )
        }
    }
}