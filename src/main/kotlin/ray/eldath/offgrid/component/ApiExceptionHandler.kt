package ray.eldath.offgrid.component

import com.fasterxml.jackson.core.exc.StreamReadException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.RouteMetaDsl
import org.http4k.core.*
import org.http4k.core.ContentType.Companion.APPLICATION_JSON
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.format.Jackson.auto
import org.http4k.lens.string
import ray.eldath.offgrid.util.ErrorCode
import ray.eldath.offgrid.util.asJsonString

data class ApiExceptionData(val code: Int, val message: String, val status: Status = BAD_REQUEST)

data class ApiException(val data: ApiExceptionData) : Exception() {

    companion object {
        operator fun invoke(code: Int, message: String, status: Status = BAD_REQUEST) =
            ApiException(ApiExceptionData(code, message, status))
    }
}

object ApiExceptionHandler {
    private class JsonParseExceptionWrapper(val message: String, val payload: String?) {
        companion object {
            val lens = Body.auto<JsonParseExceptionWrapper>().toLens()
        }
    }

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    private class JsonMappingExceptionWrapper(val message: String, val pathReference: String?) {
        companion object {
            val lens = Body.auto<JsonMappingExceptionWrapper>().toLens()
        }
    }

    private val exception = Body.auto<ApiExceptionData>().toLens()
    private val jsonStringLens = Body.string(APPLICATION_JSON).toLens()

    val filter by lazy {
        (Filter { next ->
            { request ->
                try {
                    next(request)
                } catch (e: ApiException) {
                    val data = e.data
//                    val json = Jackson.asJsonString(e)   | dont work, see http4k#361
                    Response(data.status).with(jsonStringLens of data.asJsonString())
                } catch (e: StreamReadException) {
                    val w = JsonParseExceptionWrapper(
                        "StreamReadException thrown with message ${e.message}, inward JSON string must be valid",
                        e.requestPayloadAsString
                    )
                    Response(BAD_REQUEST).with(JsonParseExceptionWrapper.lens of w)
                } catch (e: JsonMappingException) {
                    val w = JsonMappingExceptionWrapper(
                        "JsonMappingException thrown with message ${e.message}, inward JSON data must compatible with domain demand.",
                        e.pathReference
                    )
                    Response(BAD_REQUEST).with(JsonMappingExceptionWrapper.lens of w)
                }
            }
        })
    }

    fun RouteMetaDsl.exception(status: Status, code: Int, message: String) {
        returning(
            status,
            exception to ApiExceptionData(code, message, status),
            description = code.toString(),
            definitionId = code.toString()
        )
    }

    fun RouteMetaDsl.exception(vararg code: ErrorCode) {
        code.forEach { exception(it.status, it.code, it.message) }
    }
}