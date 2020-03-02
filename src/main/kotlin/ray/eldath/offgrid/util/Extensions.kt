package ray.eldath.offgrid.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.contract.RouteMetaDsl
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.*

fun <T> T.sidecar(aside: (T) -> Unit): T {
    val r = this
    aside(r)
    return r
}

fun RouteMetaDsl.allJson() {
    inJson()
    outJson()
}

fun RouteMetaDsl.inJson() {
    consumes += ContentType.APPLICATION_JSON
}

fun RouteMetaDsl.outJson() {
    produces += ContentType.APPLICATION_JSON
}

fun Response.Companion.redirectTo(uri: String, status: Status = Status.TEMPORARY_REDIRECT) =
    Response(status).header("Location", uri)

fun ByteArray.toHexString() = joinToString(separator = "") { String.format("%02x", (it.toInt() and 0xFF)) }
fun <T> Optional<T>.getOrNull(): T? = if (isEmpty) null else get()
fun Any.asJsonString(): String = jacksonObjectMapper().writeValueAsString(this)
fun Response.asJson(): JsonNode = jacksonObjectMapper().readTree(this.bodyString())