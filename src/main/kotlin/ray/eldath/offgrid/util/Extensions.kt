package ray.eldath.offgrid.util

import org.http4k.contract.RouteMetaDsl
import org.http4k.core.ContentType

fun <T> T.sidecar(aside: (T) -> Unit): T {
    val r = this
    aside(r)
    return r
}

fun RouteMetaDsl.allJson() {
    this.produces += ContentType.APPLICATION_JSON
    this.consumes += ContentType.APPLICATION_JSON
}