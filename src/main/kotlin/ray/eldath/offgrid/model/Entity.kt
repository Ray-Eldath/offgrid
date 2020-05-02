package ray.eldath.offgrid.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.core.Body
import org.http4k.format.Jackson.auto
import java.time.LocalDateTime
import java.util.*

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OutboundEntity(
    val id: String,
    val name: String,
    val tags: List<OutboundEntityTag>? = null,
    val createTime: LocalDateTime,
    val lastConnectionTime: LocalDateTime? = null
) {
    companion object {
        val lens = Body.auto<OutboundEntity>().toLens()

        val mock = OutboundEntity(
            UUID.randomUUID().toString(),
            "offgrid-test-entity-1",
            listOf(OutboundEntityTag(0, "test"), OutboundEntityTag(1, "internal")),
            createTime = LocalDateTime.now(),
            lastConnectionTime = LocalDateTime.now()
        )
    }

    data class OutboundEntityTag(val id: Int, val tag: String)
}