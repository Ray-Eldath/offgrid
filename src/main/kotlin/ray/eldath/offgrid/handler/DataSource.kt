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
import org.http4k.lens.int
import ray.eldath.offgrid.generated.offgrid.Tables
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Entity
import ray.eldath.offgrid.handler.Entities.EntityName
import ray.eldath.offgrid.handler.Entities.findById
import ray.eldath.offgrid.util.*
import java.time.LocalDateTime

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