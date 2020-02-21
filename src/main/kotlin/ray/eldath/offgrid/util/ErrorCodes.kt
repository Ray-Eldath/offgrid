package ray.eldath.offgrid.util

import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.UNAUTHORIZED
import ray.eldath.offgrid.component.ApiException

data class ErrorCode(val code: Int, val message: String, val status: Status) {

    operator fun invoke() = ApiException(code, message, status) // TODO: metric
}

object ErrorCodes {
    // 1: invalid field, only relate to the inbound data itself.
    fun commonBadRequest(message: String) = ErrorCode(100, message, BAD_REQUEST)

    val INVALID_EMAIL_ADDRESS = ErrorCode(101, "invalid email address", BAD_REQUEST)

    object InvalidRegisterSubmission {
        val USERNAME_TOO_LONG = ErrorCode(110, "username too long", BAD_REQUEST)
        val PASSWORD_TOO_SHORT = ErrorCode(111, "password too short", BAD_REQUEST)
        val PASSWORD_TOO_LONG = ErrorCode(112, "password too long", BAD_REQUEST)
    }


    // 3: invalid state or data
    fun permissionDenied(require: Collection<Permission>) = ErrorCode(
        301,
        "permission denied: require ${require.joinToString(limit = 30) { it.name }}",
        UNAUTHORIZED
    )

    val LOGIN_REQUIRED = ErrorCode(302, "login required, you should login first", UNAUTHORIZED)
    val AUTH_TOKEN_INVALID_OR_EXPIRED = ErrorCode(303, "auth token invalid or expired, try login again", UNAUTHORIZED)
    val USER_HAS_BEEN_BANNED = ErrorCode(304, "the account has been banned", UNAUTHORIZED)
    val UNCONFIRMED_EMAIL = ErrorCode(310, "unconfirmed email address", FORBIDDEN)
    val APPLICATION_PENDING = ErrorCode(311, "your register application is pending", FORBIDDEN)
    val APPLICATION_REJECTED = ErrorCode(
        312,
        "your register application as well as any further applications are rejected. consider contact the user admin to reset your register status",
        FORBIDDEN
    )
    val USER_ALREADY_REGISTERED =
        ErrorCode(313, "user with the given email has already registered in ouy system.", CONFLICT)

    val TOKEN_EXPIRED = ErrorCode(314, "given token has expired, try to request a new token.", FORBIDDEN)

    val CREATE_SURPASS_USER =
        ErrorCode(320, "can not create user that has some permissions you do not have", FORBIDDEN)
    val DELETE_SURPASS_USER = ErrorCode(321, "can not delete user that has some permissions you do not have", FORBIDDEN)
    val DELETE_SELF = ErrorCode(322, "can not delete yourself through user management API", FORBIDDEN)


    // 4: not found
    fun commonNotFound(message: String = "given entity is not found") = ErrorCode(400, message, NOT_FOUND)

    val USER_NOT_FOUND = ErrorCode(401, "incorrect email or password", NOT_FOUND)
    val TOKEN_NOT_FOUND = ErrorCode(402, "given token not found, try to request a new token.", NOT_FOUND)

    // 5: internal server error
    fun sendEmailFailed(target: String, log: String, type: String = "address confirmation email") =
        ErrorCode(
            510,
            "send $type to email address $target failed with log: \n" +
                    if (log.isEmpty()) "<empty>" else log,
            INTERNAL_SERVER_ERROR
        )
}