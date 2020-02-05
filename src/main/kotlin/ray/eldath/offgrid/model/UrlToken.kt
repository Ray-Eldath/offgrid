package ray.eldath.offgrid.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.http4k.format.Jackson
import ray.eldath.offgrid.core.Core
import ray.eldath.offgrid.util.base64Url
import ray.eldath.offgrid.util.decodeBase64Url
import java.util.*

open class UrlToken(
    open val email: String,
    open val token: String = generateToken(),
    @JsonProperty("attempt") val route: String
) {

    init {
        require(!route.startsWith("/") && !route.endsWith("/"))
    }

    val url by lazy {
        val urlToken = Jackson.asJsonString(UrlToken(email, token, route)).base64Url()

        "${Core.getEnv("OFFGRID_HOST")}/$route/$urlToken"
    }

    companion object {
        fun generateToken() = UUID.randomUUID().toString()
        fun parse(token: String): UrlToken = Jackson.asA(token.decodeBase64Url(), UrlToken::class)
    }
}