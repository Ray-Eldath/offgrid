package ray.eldath.offgrid.util

import org.http4k.contract.Tag

object RouteTag {
    val User = Tag("user", "all staff relates to manipulate or request user.")
    val Authorization = Tag("authorization", "sign-up, login, etc.")
}