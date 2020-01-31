package ray.eldath.offgrid.util

import org.http4k.contract.Tag

object RouteTag {
    val User = Tag("user", "all staff relates to manipulate or request user.")
    val Authorization = Tag("authorization", "sign-up, login, etc.")
    val Registration = Tag("registration", "anything consist of the registration procedure.")

    val Secure = Tag(
        "secure-api",
        "header `Authorization` must be properly set, or request will be rejected with status 401."
    )
}