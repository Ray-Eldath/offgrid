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
import ray.eldath.offgrid.model.OutboundState
import ray.eldath.offgrid.model.toOutbound
import ray.eldath.offgrid.util.*

class MetaUserModel(private val configuredSecurity: Security) : ContractHandler {

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class RolesEntry(val role: OutboundRole, val defaultPermissions: List<OutboundPermission>)

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class UserModelResponse(
        val userStates: List<OutboundState>,
        val treePermissions: List<OutboundTreePermission>?,
        val flattenPermissions: List<OutboundPermission>?,
        val roles: List<RolesEntry>
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class OutboundTreePermission(
        val id: String,
        val name: String,
        val children: List<OutboundTreePermission>? = null
    )

    override fun compile(): ContractRoute =
        "/meta/model/user" meta {
            summary = "Get user-related meta model"
            description =
                "Currently consisted of user-related enumeration used for modeling user, like all available" +
                        " permissions, roles and user states."
            tags += RouteTag.Meta
            security = configuredSecurity
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
                Body.auto<UserModelResponse>().toLens() of UserModelResponse(
                    userStates = UserState.values().map { OutboundState(it.id, it.name) },
                    treePermissions = Permission.Root.toTree().children,
                    flattenPermissions = Permission.values().map { OutboundPermission(it.id, it.name) },
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