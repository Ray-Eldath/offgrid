package ray.eldath.offgrid.factory

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.ContractRoute
import org.http4k.contract.RouteMetaDsl
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.uuid
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.generated.offgrid.Tables
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Entity
import ray.eldath.offgrid.generated.offgrid.tables.pojos.EntityTag
import ray.eldath.offgrid.handler.ContractHandler
import ray.eldath.offgrid.handler.Credentials
import ray.eldath.offgrid.handler.Entities
import ray.eldath.offgrid.model.OutboundEntity
import ray.eldath.offgrid.util.*
import java.time.LocalDateTime
import java.util.*

class ListEntityFactory(
    private val factoryCredentials: Credentials,
    private val factorySecurity: Security
) : EntityHandlerFactory {

    override fun makeHandler(
        route: String,
        entityType: EntityType,
        routeMetaModifier: RouteMetaDsl.() -> Unit,
        vararg requiredPermissions: Permission
    ) =
        ListEntity(entityType, requiredPermissions, route, routeMetaModifier, factoryCredentials, factorySecurity)

    class ListEntity(
        entityType: EntityType,
        requiredPermissions: Array<out Permission>,
        private val route: String,
        private val routeMetaModifier: (RouteMetaDsl) -> Unit,
        credentials: Credentials,
        private val configuredSecurity: Security
    ) : ContractHandler {

        private val handler: HttpHandler = { req ->
            credentials(req).requirePermission(*requiredPermissions)

            val pageSize =
                pageSizeLens(req)
            val page =
                pageLens(req)

            val fetched: Pair<Int, MutableList<Entity>> =
                transaction { // type inference staff: has to specify explicitly
                    val e = Tables.ENTITIES

                    val select = selectFrom(e)
                        .where(e.TYPE.eq(entityType.id))

                    fetchCount(select) to
                            select.limit(pageSize).offset((page - 1) * pageSize).fetchInto(Entity::class.java)
                }

            val mapped = fetched.second.map {
                val tags = transaction {
                    val et = Tables.ENTITY_TAGS

                    val select = selectFrom(et)
                        .where(et.ENTITY_ID.eq(it.id))

                    select.fetchInto(EntityTag::class.java) // TODO: bad performance. try to optimize with table join.
                }

                OutboundEntity(
                    id = it.id,
                    name = it.name,
                    tags = tags.map { t -> t.tag },
                    createTime = it.createTime,
                    lastConnectionTime = it.lastConnectionTime
                )
            }

            Response(Status.OK).with(
                responseLens of ListResponse(
                    fetched.first,
                    mapped
                )
            )
        }

        override fun compile(): ContractRoute =
            route meta {
                routeMetaModifier(this)

                queries += pageLens
                queries += pageSizeLens
                security = configuredSecurity
                outJson()
                returning(
                    Status.OK, responseLens to ListResponse(
                        1,
                        listOf(OutboundEntity.mock)
                    )
                )
            } bindContract Method.GET to handler

        companion object {
            private val responseLens = Body.auto<ListResponse>().toLens()

            private val pageLens = Query.int().defaulted("page", 1, "the n-th page of result")
            private val pageSizeLens =
                Query.int().defaulted("pre_page", 10, "the size of elements that one page should contain.")

            data class ListResponse(val total: Int, val result: List<OutboundEntity>)
        }
    }
}

class CreateEntityFactory(
    private val factoryCredentials: Credentials,
    private val factorySecurity: Security
) : EntityHandlerFactory {

    override fun makeHandler(
        route: String,
        entityType: EntityType,
        routeMetaModifier: RouteMetaDsl.() -> Unit,
        vararg requiredPermissions: Permission
    ) =
        CreateEntity(entityType, requiredPermissions, route, routeMetaModifier, factoryCredentials, factorySecurity)

    class CreateEntity(
        entityType: EntityType,
        requiredPermissions: Array<out Permission>,
        private val route: String,
        private val routeMetaModifier: (RouteMetaDsl) -> Unit,
        credentials: Credentials,
        private val configuredSecurity: Security
    ) : ContractHandler {
        private val handler: HttpHandler = { req ->
            credentials(req).requirePermission(*requiredPermissions)

            val (name) = Entities.EntityName.lens(req)
            val accessKey = AccessKey.generate()

            transaction {
                newRecord(Tables.ENTITIES).apply {
                    this.name = name
                    type = entityType.id
                    accessKeyId = accessKey.id
                    accessKeySecret = accessKey.secret
                    createTime = LocalDateTime.now()
                }.also { it.store() }
            }.let {
                Response(Status.OK).with(
                    responseLens of CreateDataSourceResponse(
                        id = it.id,
                        accessKeyId = accessKey.id,
                        accessKeySecret = accessKey.secret
                    )
                )
            }
        }

        override fun compile(): ContractRoute =
            route meta {
                routeMetaModifier(this)

                security = configuredSecurity
                allJson()
                receiving(Entities.EntityName.lens to Entities.EntityName.mock)
            } bindContract Method.PUT to handler

        companion object {
            @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
            data class CreateDataSourceResponse(val id: String, val accessKeyId: String, val accessKeySecret: String)

            private val responseLens = Body.auto<CreateDataSourceResponse>().toLens()
        }
    }
}

class ModifyEntityFactory(
    private val factoryCredentials: Credentials,
    private val factorySecurity: Security
) : EntityHandlerFactory {

    override fun makeHandler(
        route: String,
        entityType: EntityType,
        routeMetaModifier: RouteMetaDsl.() -> Unit,
        vararg requiredPermissions: Permission
    ) =
        ModifyEntity(route, requiredPermissions, routeMetaModifier, factoryCredentials, factorySecurity)

    class ModifyEntity(
        private val route: String,
        private val requiredPermissions: Array<out Permission>,
        private val routeMetaModifier: (RouteMetaDsl) -> Unit,
        private val credentials: Credentials,
        private val configuredSecurity: Security
    ) : ContractHandler {

        private fun handler(id: UUID): HttpHandler = { req ->
            credentials(req).requirePermission(*requiredPermissions)

            val (name) = Entities.EntityName.lens(req)
            transaction {
                val e = Tables.ENTITIES
                val ds = Entities.findById(id.toString()) ?: throw ErrorCodes.commonNotFound()()

                update(e)
                    .set(e.NAME, name)
                    .where(e.ID.eq(ds.id)).execute()
            }

            Response(Status.OK)
        }

        override fun compile(): ContractRoute =
            route / Path.uuid().of("entityId", routeDesc) meta {
                routeMetaModifier(this)

                security = configuredSecurity
                inJson()
                receiving(Entities.EntityName.lens to Entities.EntityName.mock)
                returning(Status.OK to "name of the specified entity has been successfully update.")
                exception(ErrorCodes.commonNotFound())
            } bindContract Method.PATCH to ::handler

        companion object {
            const val routeDesc = "id of the entity, i.e. DataSource or Endpoint, represented as UUID"
        }
    }
}

interface EntityHandlerFactory {
    fun makeHandler(
        route: String,
        entityType: EntityType,
        routeMetaModifier: RouteMetaDsl.() -> Unit,
        vararg requiredPermissions: Permission
    ): ContractHandler
}