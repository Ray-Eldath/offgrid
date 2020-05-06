package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.lens.uuid
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.generated.offgrid.tables.Entities
import ray.eldath.offgrid.generated.offgrid.tables.EntityTags
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Entity
import ray.eldath.offgrid.generated.offgrid.tables.pojos.EntityTag
import ray.eldath.offgrid.util.*
import java.util.*

class TagEntity(private val credentials: Credentials, private val configuredSecurity: Security) : ContractHandler {
    data class TagEntityRequest(val tag: String) {
        fun check() {
            if (tag.length >= 20)
                throw ErrorCodes.InvalidEntityTag.TOO_LONG()
        }
    }

    data class TagEntityResponse(val id: Int)

    private fun handler(uuid: UUID, useless: String): HttpHandler = { req: Request ->
        val (tagString) = requestLens(req).also { it.check() }

        transaction {
            val e = Entities.ENTITIES
            val et = EntityTags.ENTITY_TAGS

            val entity =
                selectFrom(e)
                    .where(e.ID.eq(uuid.toString()))
                    .fetchOptionalInto(Entity::class.java)
                    .getOrThrow { ErrorCodes.commonNotFound()() }

            if (entity.type == EntityType.DataSource.id)
                credentials(req).requirePermission(Permission.ModifyDataSource)
            else if (entity.type == EntityType.Endpoint.id)
                credentials(req).requirePermission(Permission.ModifyEndpoint)

            val record = newRecord(et)

            record.apply {
                entityId = entity.id
                entityType = entity.type
                tag = tagString
            }.insert()

            record
        }.let { Response(Status.OK).with(responseLens of TagEntityResponse(it.id)) }
    }

    override fun compile() =
        "/entity" / Path.uuid().of("entityUUID", "UUID of DataSource or Endpoint") / "tag" meta {
            summary = "Tag an entity"
            description = "Entity can be a DataSource or an Endpoint."
            tags += RouteTag.EntityTag
            security = configuredSecurity

            allJson()
            exception(ErrorCodes.InvalidEntityTag.TOO_LONG)
            returning(Status.OK, responseLens to TagEntityResponse(1103))
            receiving(requestLens to TagEntityRequest("demo"))
        } bindContract Method.POST to ::handler

    companion object {
        val requestLens = Body.auto<TagEntityRequest>().toLens()
        val responseLens = Body.auto<TagEntityResponse>().toLens()
    }
}

class UntagEntity(
    private val credentials: Credentials,
    private val configuredSecurity: Security
) : ContractHandler {

    private fun handler(id: Int): HttpHandler = { req: Request ->
        transaction {
            val et = EntityTags.ENTITY_TAGS

            selectFrom(et)
                .where(et.ID.eq(id)).fetchOptionalInto(EntityTag::class.java)
                .getOrThrow { ErrorCodes.commonNotFound()() }
                .run {
                    if (entityType == EntityType.DataSource.id)
                        credentials(req).requirePermission(Permission.ModifyDataSource)
                    else if (entityType == EntityType.Endpoint.id)
                        credentials(req).requirePermission(Permission.ModifyEndpoint)
                }

            deleteFrom(et)
                .where(et.ID.eq(id)).execute()
        }

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/entity/tags" / Path.int().of("tagId", "Id of the tag.") meta {
            summary = "Untag an entity"
            description = "Entity can be a DataSource or an Endpoint."
            tags += RouteTag.EntityTag
            security = configuredSecurity

            exception(ErrorCodes.InvalidEntityTag.TOO_LONG)
            returning(Status.OK to "specified tag has been successfully deleted.")
        } bindContract Method.DELETE to ::handler
}