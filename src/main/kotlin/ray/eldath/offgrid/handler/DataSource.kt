package ray.eldath.offgrid.handler

import org.http4k.contract.security.Security
import org.http4k.core.Body
import org.http4k.format.Jackson.auto
import ray.eldath.offgrid.factory.CreateEntityFactory
import ray.eldath.offgrid.factory.ListEntityFactory
import ray.eldath.offgrid.factory.ModifyEntityFactory
import ray.eldath.offgrid.generated.offgrid.Tables
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Entity
import ray.eldath.offgrid.util.*

class ListDataSource(credentials: Credentials, security: Security) :
    ContractHandler by ListEntityFactory(credentials, security).makeHandler("/datasource", EntityType.DataSource, {
        summary = "List datasource"
        tags += RouteTag.DataSource
    }, Permission.ListDataSource)

class CreateDataSource(credentials: Credentials, security: Security) :
    ContractHandler by CreateEntityFactory(credentials, security).makeHandler("/datasource", EntityType.DataSource, {
        summary = "Create datasource"
        tags += RouteTag.DataSource
    }, Permission.CreateDataSource)

class ModifyDataSource(credentials: Credentials, security: Security) :
    ContractHandler by ModifyEntityFactory(credentials, security).makeHandler("/datasource", EntityType.DataSource, {
        summary = "Modify datasource"
        tags += RouteTag.DataSource
    }, Permission.ModifyDataSource)

object Entities {
    data class EntityName(val name: String) {
        companion object {
            val lens = Body.auto<EntityName>().toLens()

            val mock = EntityName("offgrid-test-entity-1")
        }
    }

    fun findById(id: String): Entity? =
        transaction {
            val e = Tables.ENTITIES
            selectFrom(e)
                .where(e.ID.eq(id))
                .fetchOptional { it.into(e).into(Entity::class.java) }.getOrNull()
        }
}