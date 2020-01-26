package ray.eldath.offgrid.component

import com.github.benmanes.caffeine.cache.Caffeine
import de.mkammerer.argon2.Argon2Factory
import org.http4k.contract.security.Security
import org.http4k.core.Filter
import org.http4k.core.RequestContexts
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.lens.RequestContextKey
import ray.eldath.offgrid.dao.User
import ray.eldath.offgrid.util.sidecar
import java.util.*
import java.util.concurrent.TimeUnit

object BearerSecurity : Security {
    const val EXPIRY_MINUTES = 60

    private val contexts = RequestContexts()
    val credentials = RequestContextKey.required<User>(contexts)

    private val caffeine = Caffeine.newBuilder().apply {
        expireAfterAccess(EXPIRY_MINUTES.toLong(), TimeUnit.MINUTES)
        recordStats()
    }.build<String, User>()

    fun authorize(user: User): String =
        UUID.randomUUID().toString().sidecar {
            caffeine.put(it, user)
        }

    fun invalidate(bearer: String) {
        caffeine.invalidate(bearer)
    }

    override val filter: Filter by lazy {
        ServerFilters.InitialiseRequestContext(contexts)
            .then(ServerFilters.BearerAuth(credentials) {
                caffeine.getIfPresent(it)
            })
    }
}

object Argon2 {
    private val argon2 = Argon2Factory.create()

    fun hash(password: String): String {
        val a = password.toByteArray()
        return argon2.hash(10, 65536, 1, a).sidecar {
            argon2.wipeArray(a)
        }
    }

    fun verify(hashedPassword: ByteArray, password: ByteArray): Boolean {
        return argon2.verify(String(hashedPassword), password).sidecar {
            argon2.wipeArray(password)
        }
    }
}