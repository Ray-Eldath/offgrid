package ray.eldath.offgrid.component

import com.github.benmanes.caffeine.cache.Caffeine
import de.mkammerer.argon2.Argon2Factory
import org.http4k.contract.security.Security
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.RequestContexts
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.lens.RequestContextKey
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.util.ErrorCodes.LOGIN_REQUIRED
import ray.eldath.offgrid.util.ErrorCodes.commonBadRequest
import ray.eldath.offgrid.util.ErrorCodes.permissionDenied
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.Permission.Companion.expand
import ray.eldath.offgrid.util.sidecar
import java.util.*
import java.util.concurrent.TimeUnit

data class InboundUser(
    val user: User,
    val authorization: Authorization,
    val extraPermission: Collection<ExtraPermission>
) {
    val permissions: Set<Permission> by lazy {
        val r = hashSetOf<Permission>()

        r.addAll(authorization.role.defaultPermissions.expand())
        r.addAll(extraPermission.filter { it.isShield == false }.flatMap { it.permissionId.expand() })
        extraPermission.filter { it.isShield }.forEach { r.removeAll(it.permissionId.expand()) }

        r
    }

    fun requirePermission(vararg requires: Permission) {
        requires.expand().filterNot { permissions.contains(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { throw permissionDenied(it)() }
    }
}

object BearerSecurity : Security {
    const val EXPIRY_MINUTES = 60

    private val contexts = RequestContexts()
    val credentials = RequestContextKey.required<InboundUser>(contexts)

    private val caffeine = Caffeine.newBuilder().apply {
        expireAfterAccess(EXPIRY_MINUTES.toLong(), TimeUnit.MINUTES)
        recordStats()
    }.build<String, InboundUser>()

    fun authorize(user: InboundUser): String =
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

    fun Request.bearerToken(): String =
        ((header("Authorization") ?: throw LOGIN_REQUIRED())
            .trim()
            .takeIf { it.startsWith("Bearer") } ?: throw commonBadRequest("malformed Bearer")())
            .substringAfter("Bearer")
            .trim()
}

object Argon2 {
    private val argon2 = Argon2Factory.create()

    fun hash(password: ByteArray): String {
        return argon2.hash(10, 65536, 1, password).sidecar {
            argon2.wipeArray(password)
        }
    }

    fun verify(hashedPassword: String, password: ByteArray): Boolean {
        return argon2.verify(hashedPassword, password).sidecar {
            argon2.wipeArray(password)
        }
    }
}