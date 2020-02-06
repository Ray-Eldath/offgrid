package ray.eldath.offgrid.model

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import ray.eldath.offgrid.component.Md5
import ray.eldath.offgrid.util.ErrorCodes
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.UserRole

data class UsernamePassword(val username: String, val password: String) {
    companion object {
        private const val MAX_USERNAME_LENGTH = 16
        private const val MAX_PASSWORD_LENGTH = 18
        private const val MIN_PASSWORD_LENGTH = 6

        fun checkPassword(p: String): Unit =
            p.length.let {
                if (it <= MIN_PASSWORD_LENGTH)
                    throw ErrorCodes.InvalidRegisterSubmission.PASSWORD_TOO_SHORT()
                if (it > MAX_PASSWORD_LENGTH)
                    throw ErrorCodes.InvalidRegisterSubmission.PASSWORD_TOO_LONG()
            }

        fun checkUsername(u: String): Unit =
            u.length.let {
                if (it > MAX_USERNAME_LENGTH)
                    throw ErrorCodes.InvalidRegisterSubmission.USERNAME_TOO_LONG()
            }
    }

    fun check() {
        checkPassword(password)
        checkUsername(username)
    }
}

/**
 * @param permissions not extra permissions but all permissions
 */
data class OutboundUser(
    val id: Int,
    val username: String,
    val email: String,
    val role: OutboundRole,
    val permissions: Collection<OutboundPermission>,
    val avatarUrl: String = "https://cdn.v2ex.com/gravatar/${Md5.hash(email)}.jpg?r=g&d=retro&s=200"
) {

    companion object {
        val mock = OutboundUser(
            345142,
            "Ray Eldath",
            "alpha.beta@omega.com",
            UserRole.Root.toOutbound(),
            UserRole.Root.defaultPermissions
                .also { it.toMutableList().remove(Permission.ComputationResult) }
                .toOutbound()
        )
    }
}

data class OutboundPermission(val id: String, val name: String)

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
data class InboundExtraPermission(val id: String, val isShield: Boolean) {
    init {
        if (id !in allPermissionsId)
            throw (ErrorCodes.commonBadRequest("invalid extraPermissionsId $id, note that id is required and name is illegal"))()
    }

    companion object {
        private val allPermissionsId = Permission.values().map { p -> p.id }
    }
}

data class OutboundRole(val id: Int, val name: String)

fun Permission.toExtraExchangeable(isShield: Boolean) = InboundExtraPermission(id, isShield)

fun Collection<Permission>.toOutbound() = map { it.toOutbound() }
fun Permission.toOutbound() = OutboundPermission(id, name)
fun UserRole.toOutbound() = OutboundRole(id, name)