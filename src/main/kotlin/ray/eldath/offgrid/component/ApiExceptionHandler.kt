package ray.eldath.offgrid.component

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.contract.RouteMetaDsl
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.string
import ray.eldath.offgrid.util.ErrorCode

data class ApiExceptionData(val code: Int, val message: String, val status: Status = Status.BAD_REQUEST)

data class ApiException(val data: ApiExceptionData) : Exception() {

    companion object {
        operator fun invoke(code: Int, message: String, status: Status = Status.BAD_REQUEST) =
            ApiException(ApiExceptionData(code, message, status))
    }
}

object ApiExceptionHandler {

    private val exception = Body.auto<ApiExceptionData>().toLens()

    val filter by lazy {
        (Filter { next ->
            { request ->
                try {
                    next(request)
                } catch (e: ApiException) {
                    val data = e.data
//                    val json = Jackson.asJsonString(e)   | dont work, see http4k#361
                    val json = jacksonObjectMapper().writeValueAsString(data)
                    Response(data.status).with(Body.string(ContentType.APPLICATION_JSON).toLens() of json)
                }
            }
        })
    }

    fun RouteMetaDsl.exception(status: Status, code: Int, message: String) {
        this.returning(status, exception to ApiExceptionData(code, message, status))
    }

    fun RouteMetaDsl.exception(code: ErrorCode) {
        this.exception(code.status, code.code, code.message)
    }

    fun RouteMetaDsl.exception(vararg code: ErrorCode) {
        code.forEach { exception(it) }
    }
}