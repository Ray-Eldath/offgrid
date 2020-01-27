package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import ray.eldath.offgrid.component.ApiException
import ray.eldath.offgrid.component.ApiExceptionHandler.exception
import ray.eldath.offgrid.util.allJson

class Test(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class TestRequest(val str: String)
    data class TestResponse(val int: Int)

    private val requestLens = Body.auto<TestRequest>("Login essentials.").toLens()
    private val responseLens = Body.auto<TestResponse>("The bearer and expire.").toLens()

    private fun handler(): HttpHandler = { request ->
        val received = requestLens(request)

        if (received.str == "a")
            throw ApiException(401, "123123")
        else Response(Status.OK).with(responseLens of TestResponse(received.str.toInt()))
    }

    override fun compile(): ContractRoute {

        return "/test" meta {
            summary = "Test"
            allJson()

            receiving(requestLens to TestRequest("123"))
            returning(Status.OK, responseLens to TestResponse(123))
            exception(Status.TEMPORARY_REDIRECT, 401, "123123")
        } bindContract Method.POST to handler()
    }
}