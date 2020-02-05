package ray.eldath.offgrid.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.contract.RouteMetaDsl
import org.http4k.core.ContentType

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

fun Any.json(): String = jacksonObjectMapper().writeValueAsString(this)