package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import ray.eldath.offgrid.factory.Entities
import ray.eldath.offgrid.generated.offgrid.tables.EntityRoutes
import ray.eldath.offgrid.generated.offgrid.tables.pojos.EntityRoute
import ray.eldath.offgrid.util.*
import ray.eldath.offgrid.util.ErrorCodes.commonNotFound
import java.util.*

class ListRoute(private val credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class ListResponseEntry(val id: Int, val state: Int, val from: String, val to: String)
    data class ListResponse(val total: Int, val result: List<ListResponseEntry>)

    private val pageLens = Query.int().defaulted("page", 1, "the n-th page of result")
    private val pageSizeLens =
        Query.int().defaulted("pre_page", 10, "the size of elements that one page should contain.")

    private val handler: HttpHandler = { req ->
        credentials(req).requirePermission(Permission.Graph)

        val page = pageLens(req)
        val pageSize = pageSizeLens(req)

        transaction {
            val r = EntityRoutes.ENTITY_ROUTES

            fetchCount(r) to
                    selectFrom(r).limit(pageSize).offset(Pagination.offset(page, pageSize))
                        .fetchInto(EntityRoute::class.java)
                        .map { ListResponseEntry(it.id, it.state, it.fromId, it.toId) }
        }.let { Response(Status.OK).with(responseLens of ListResponse(it.first, it.second)) }
    }

    override fun compile() =
        "/routes" meta {
            summary = "List routes"
            description = "List all routes. For field \"state\", `0` stands for enbled and `1` stands for disabled."
            tags += RouteTag.Route
            security = configuredSecurity
            outJson()

            returning(Status.OK to "Specified route has been created and enabled.")
        } bindContract Method.POST to handler

    companion object {
        val responseLens = Body.auto<ListResponse>().toLens()
    }
}

class CreateRoute(private val credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {
    data class CreateRouteRequest(val from: UUID, val to: UUID)

    private val handler: HttpHandler = { req ->
        credentials(req).requirePermission(Permission.Graph)

        val (from, to) = requestLens(req)
        if (Entities.findById(from) == null || Entities.findById(to) == null)
            throw commonNotFound()()

        transaction {
            val r = EntityRoutes.ENTITY_ROUTES

            newRecord(r).apply {
                fromId = from.toString()
                toId = to.toString()
            }.store()
        }

        Response(Status.OK)
    }

    override fun compile() =
        "/routes" meta {
            summary = "Create route"
            description = "Create a route between specified Data Source and Endpoint."
            tags += RouteTag.Route
            security = configuredSecurity
            inJson()

            receiving(requestLens to CreateRouteRequest(UUID.randomUUID(), UUID.randomUUID()))
            returning(Status.OK to "Specified route has been created and enabled.")
        } bindContract Method.POST to handler

    companion object {
        private val requestLens = Body.auto<CreateRouteRequest>().toLens()
    }
}

class EnableRoute(private val credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {
    private fun handler(id: Int, useless: String): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.Graph)

        val route = Routes.findById(id) ?: throw commonNotFound()()
        transaction {
            val r = EntityRoutes.ENTITY_ROUTES

            update(r)
                .set(r.STATE, 0)
                .where(r.ID.eq(route.id)).execute()
        }

        Response(Status.OK)
    }

    override fun compile() =
        "/routes" / Path.int().of("routeId", "Id of the route") / "enable" meta {
            summary = "Enable route"
            description = "Enable a disabled route."
            tags += RouteTag.Route
            security = configuredSecurity

            returning(Status.OK to "Specified route has been enabled.")
        } bindContract Method.GET to ::handler
}

class DisableRoute(private val credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {
    private fun handler(id: Int, useless: String): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.Graph)

        val route = Routes.findById(id) ?: throw commonNotFound()()
        transaction {
            val r = EntityRoutes.ENTITY_ROUTES

            update(r)
                .set(r.STATE, 1)
                .where(r.ID.eq(route.id)).execute()
        }

        Response(Status.OK)
    }

    override fun compile() =
        "/routes" / Path.int().of("routeId", "Id of the route") / "disable" meta {
            summary = "Disable route"
            description = "Disable an enabled route."
            tags += RouteTag.Route
            security = configuredSecurity

            returning(Status.OK to "Specified route has been disabled.")
        } bindContract Method.GET to ::handler
}

class DeleteRoute(private val credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {
    private fun handler(id: Int): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.Graph)

        val route = Routes.findById(id) ?: throw commonNotFound()()
        transaction {
            val r = EntityRoutes.ENTITY_ROUTES

            deleteFrom(r)
                .where(r.ID.eq(route.id)).execute()
        }

        Response(Status.OK)
    }

    override fun compile() =
        "/routes" / Path.int().of("routeId", "Id of the route") meta {
            summary = "Delete route"
            description = "Delete a route."
            tags += RouteTag.Route
            security = configuredSecurity

            returning(Status.OK to "Specified route has been deleted.")
        } bindContract Method.DELETE to ::handler
}

object Routes {
    fun findById(id: Int): EntityRoute? =
        transaction {
            val r = EntityRoutes.ENTITY_ROUTES

            selectFrom(r)
                .where(r.ID.eq(id))
                .fetchOptionalInto(EntityRoute::class.java).getOrNull()
        }
}