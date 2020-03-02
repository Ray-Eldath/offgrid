package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import ray.eldath.offgrid.util.ErrorCodes
import ray.eldath.offgrid.util.RouteTag

class Echo : ContractHandler {

    private val handler: HttpHandler = {
        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/_echo" meta {
            summary = "Just return a 200"
            tags += RouteTag.Debug

            returning(Status.OK)
        } bindContract Method.GET to handler
}

class Require : ContractHandler {

    private val states = mapOf(
        "ok" to Response(Status.OK),
        "400" to Response(Status.UNAUTHORIZED),
        "500" to Response(Status.INTERNAL_SERVER_ERROR)
    )

    private val exceptions = mapOf(
        "lr" to ErrorCodes.LOGIN_REQUIRED()
    )

    private fun handler(type: String): HttpHandler = {
        val exception = exceptions.entries.firstOrNull { it.key == type }
        if (exception != null)
            throw exception.value

        states.entries.firstOrNull { it.key == type }?.value ?: Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/_require" / Path.of("type", "Type of exception that should returns") meta {
            summary = "Just returns specified exception or status"
            tags += RouteTag.Debug

            states.forEach { (t, u) -> returning(t to u) }
            exceptions.forEach { (t, u) -> returning(u.data.status to t) }
        } bindContract Method.GET to ::handler
}