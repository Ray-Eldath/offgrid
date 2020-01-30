package ray.eldath.offgrid.util

import org.http4k.core.Status
import ray.eldath.offgrid.component.ApiException

data class ErrorCode(val code: Int, val message: String, val status: Status) {

    operator fun invoke() = ApiException(code, message, status)
}

object ErrorCodes {
    // 1: invalid field, only relate to the inbound data itself.
    fun commonBadRequest(message: String) = ErrorCode(100, message, Status.BAD_REQUEST)

    val INVALID_EMAIL_ADDRESS = ErrorCode(101, "invalid email address", Status.BAD_REQUEST)


    // 3: invalid state or data
    fun permissionDenied(require: Collection<Permission>) = ErrorCode(
        301,
        "permission denied: require ${require.joinToString(limit = 30) { it.name }}",
        Status.UNAUTHORIZED
    )

    val LOGIN_REQUIRED = ErrorCode(302, "login required, you should login first", Status.UNAUTHORIZED)
    val UNCONFIRMED_EMAIL = ErrorCode(310, "unconfirmed email address", Status.UNAUTHORIZED)


    // 4: not found
    val USER_NOT_FOUND = ErrorCode(401, "incorrect email or password", Status.UNAUTHORIZED)
}