package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.generated.offgrid.Tables
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Entity
import ray.eldath.offgrid.generated.offgrid.tables.pojos.EntityTag
import ray.eldath.offgrid.handler.Entities.EntityName
import ray.eldath.offgrid.handler.Entities.findById
import ray.eldath.offgrid.model.OutboundEntity
import ray.eldath.offgrid.util.*
import java.time.LocalDateTime

class ListDataSource(credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {
    private val pageLens = Query.int().defaulted("page", 1, "the n-th page of result")
    private val pageSizeLens =
        Query.int().defaulted("pre_page", 10, "the size of elements that one page should contain.")

    data class ListResponse(val total: Int, val result: List<OutboundEntity>)

    private val handler: HttpHandler = { req ->
        credentials(req).requirePermission(Permission.ListDataSource)

        val pageSize = pageSizeLens(req)
        val page = pageLens(req)

        val fetched: Pair<Int, MutableList<Entity>> = transaction { // type inference things: has to specify explicitly
            val e = Tables.ENTITIES

            val select = selectFrom(e)
                .where(e.TYPE.eq(EntityType.DataSource.id))

            fetchCount(select) to
                    select.limit(pageSize).offset((page - 1) * pageSize).fetchInto(Entity::class.java)
        }

        val mapped = fetched.second.map {
            val tags = transaction {
                val et = Tables.ENTITY_TAGS

                val select = selectFrom(et)
                    .where(et.ENTITY_ID.eq(it.id))

                select.fetchInto(EntityTag::class.java)
            }

            OutboundEntity(
                id = it.id,
                name = it.name,
                tags = tags.map { t -> t.tag },
                createTime = it.createTime,
                lastConnectionTime = it.lastConnectionTime
            )
        }

        Response(Status.OK).with(responseLens of ListResponse(fetched.first, mapped))
    }

    override fun compile(): ContractRoute =
        "/datasource" meta {
            summary = "List datasource"
            tags += RouteTag.DataSource

            queries += pageLens
            queries += pageSizeLens
            security = configuredSecurity
            outJson()
            returning(Status.OK, responseLens to ListResponse(1, listOf(OutboundEntity.mock)))
        } bindContract Method.GET to handler

    companion object {
        private val responseLens = Body.auto<ListResponse>().toLens()
    }
}

class CreateDataSource(credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class CreateDataSourceResponse(val id: Int, val accessKeyId: String, val accessKeySecret: String)

    private val handler: HttpHandler = { req ->
        credentials(req).requirePermission(Permission.CreateDataSource)

        val (name) = EntityName.lens(req)
        val accessKey = AccessKey.generate()
        transaction {
            val e = Tables.ENTITIES

            val datasource = newRecord(e).apply {
                this.name = name
                type = EntityType.DataSource.id
                accessKeyId = accessKey.id
                accessKeySecret = accessKey.secret
                createTime = LocalDateTime.now()
            }

            datasource
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
        "/datasource" meta {
            summary = "Create a new datasource"
            tags += RouteTag.DataSource

            security = configuredSecurity
            allJson()
            receiving(EntityName.lens to EntityName.mock)
        } bindContract Method.PUT to handler

    companion object {
        private val responseLens = Body.auto<CreateDataSourceResponse>().toLens()
    }
}

class ModifyDataSource(private val credentials: Credentials, private val configuredSecurity: Security) :
    ContractHandler {

    private fun handler(id: Int): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.ModifyDataSource)

        val (name) = EntityName.lens(req)
        transaction {
            val e = Tables.ENTITIES
            val ds = findById(id) ?: throw ErrorCodes.commonNotFound()()

            update(e)
                .set(e.NAME, name)
                .where(e.ID.eq(ds.id))
        }

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/datasource" / Path.int().of("datasourceId", "id of the datasource") meta {
            summary = "Modify the datasource"
            tags += RouteTag.DataSource

            security = configuredSecurity
            inJson()
            receiving(EntityName.lens to EntityName.mock)
            returning(Status.OK to "the name has been successfully update.")
            exception(ErrorCodes.commonNotFound())
        } bindContract Method.PATCH to ::handler
}

object Entities {
    data class EntityName(val name: String) {
        companion object {
            val lens = Body.auto<EntityName>().toLens()

            val mock = EntityName("offgrid-test-ds-1")
        }
    }

    fun findById(id: Int): Entity? =
        transaction {
            val e = Tables.ENTITIES
            selectFrom(e)
                .where(e.ID.eq(id))
                .fetchOptional { it.into(e).into(Entity::class.java) }.getOrNull()
        }
}