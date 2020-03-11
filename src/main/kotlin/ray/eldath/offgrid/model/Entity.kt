package ray.eldath.offgrid.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.core.Body
import org.http4k.format.Jackson.auto
import java.time.LocalDateTime

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OutboundEntity(
    val id: Int,
    val name: String,
    val tags: List<String>? = null,
    val createTime: LocalDateTime,
    val lastConnectionTime: LocalDateTime? = null
) {
    companion object {
        val lens = Body.auto<OutboundEntity>().toLens()

        val mock = OutboundEntity(
            10231,
            "offgrid-test-entity-1",
            listOf("test", "internal"),
            createTime = LocalDateTime.now(),
            lastConnectionTime = LocalDateTime.now()
        )
    }
}