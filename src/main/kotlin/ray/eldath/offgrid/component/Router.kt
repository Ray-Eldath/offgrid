package ray.eldath.offgrid.component

import org.http4k.client.OkHttp
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.uuid
import ray.eldath.offgrid.generated.offgrid.tables.Entities
import ray.eldath.offgrid.generated.offgrid.tables.EntityRoutes
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Entity
import ray.eldath.offgrid.generated.offgrid.tables.pojos.EntityRoute
import ray.eldath.offgrid.handler.ContractHandler
import ray.eldath.offgrid.util.ErrorCodes
import ray.eldath.offgrid.util.getOrNull
import ray.eldath.offgrid.util.transaction
import java.time.LocalDateTime
import java.util.*

object Router {
    private const val ACCESS_KEY_ID_HEADER = "X-Offgrid-AccessKeyId"
    private const val ACCESS_KEY_SECRET_HEADER = "X-Offgrid-AccessKeySecret"

    private val routes = hashMapOf<UUID, BodyPoster>()

    init {
        flushRoute()
    }

    fun flushRoute() {
        transaction {
            val r = EntityRoutes.ENTITY_ROUTES
            val e = Entities.ENTITIES

            selectFrom(r)
                .where(r.STATE.eq(0))
                .fetchInto(EntityRoute::class.java)
                .map {
                    val to =
                        selectFrom(e)
                            .where(e.ID.eq(it.toId))
                            .fetchOneInto(Entity::class.java)

                    UUID.fromString(it.fromId) to BodyPosterImpl(to.postUrl)
                }
        }.let { routes.putAll(it.toMap()) }
    }

    object PostData : ContractHandler {

        private fun handler(id: UUID): HttpHandler = { req ->
            val keyId = req.header(ACCESS_KEY_ID_HEADER)
                ?: throw ErrorCodes.commonBadRequest("$ACCESS_KEY_ID_HEADER not provided.")()
            val keySecret = req.header(ACCESS_KEY_SECRET_HEADER)
                ?: throw ErrorCodes.commonBadRequest("$ACCESS_KEY_SECRET_HEADER not provided.")()

            if (!verifyEntity(id.toString(), keyId, keySecret))
                throw ErrorCodes.AUTH_TOKEN_INVALID_OR_EXPIRED()

            routes[id]?.post(req.body)

            Response(Status.OK)
        }

        override fun compile() =
            "/post_data" / Path.uuid().of("dataSourceId", "Id of the DataSource.") meta {
                summary = "Post data to Endpoint"
                description = "Post data to Endpoint specified by route, agnostic of the actual format."
            } bindContract Method.POST to ::handler
    }

    private fun verifyEntity(id: String, accessKeyId: String, accessKeySecret: String): Boolean =
        transaction {
            val e = Entities.ENTITIES

            val entity = selectFrom(e)
                .where(e.ID.eq(id)).fetchOptionalInto(Entity::class.java).getOrNull()

            entity?.let {
                update(e)
                    .set(e.LAST_CONNECTION_TIME, LocalDateTime.now())
                    .where(e.ID.eq(id)).execute()

                accessKeyId == accessKeyId && entity.accessKeySecret == accessKeySecret
            } ?: false
        }
}

interface BodyPoster {
    fun post(body: Body): Response

    companion object {
        val httpClient = OkHttp()
    }
}

class BodyPosterImpl(private val postUrl: String) : BodyPoster {
    override fun post(body: Body) = BodyPoster.httpClient(Request(Method.POST, postUrl).body(body))
}