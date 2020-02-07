package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.security.Security
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status

class Echo(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {

    private val handler: HttpHandler = {
        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/echo" bindContract Method.GET to handler
}