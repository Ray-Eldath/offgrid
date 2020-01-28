package ray.eldath.offgrid.util

import org.http4k.core.Status
import ray.eldath.offgrid.component.ApiException

enum class ErrorCode(val code: Int, val message: String, val status: Status) {
    // 1: invalid field, only relate to the inbound data itself.
    INVALID_EMAIL_ADDRESS(100, "invalid email address", Status.BAD_REQUEST),

    // 3: invalid state or data
    UNCONFIRMED_EMAIL(300, "unconfirmed email address", Status.UNAUTHORIZED),

    // 4: not found
    USER_NOT_FOUND(400, "incorrect email or password", Status.UNAUTHORIZED);

    operator fun invoke() = ApiException(code, message, status)
}