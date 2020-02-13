package ray.eldath.offgrid.model

import org.http4k.format.Jackson
import ray.eldath.offgrid.core.Core
import ray.eldath.offgrid.util.base64Url
import ray.eldath.offgrid.util.decodeBase64Url
import java.util.*

open class UrlToken(open val email: String, open val token: String = generateToken(), private val route: String) {

    init {
        require(!route.startsWith("/") && !route.endsWith("/"))
    }

    val url: String by lazy {
        val urlToken = Jackson.asJsonString(SerializableUrlToken(email, token, route)).base64Url()

        "${Core.getEnv("OFFGRID_HOST")}/$route/$urlToken"
    }

    private data class SerializableUrlToken(val email: String, val token: String, val attempt: String) {
        fun to() = UrlToken(email, token, attempt)
    }

    companion object {
        fun generateToken() = UUID.randomUUID().toString()
        fun parse(token: String): UrlToken = Jackson.asA(token.decodeBase64Url(), SerializableUrlToken::class).to()
    }
}