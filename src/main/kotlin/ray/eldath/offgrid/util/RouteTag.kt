package ray.eldath.offgrid.util

import org.http4k.contract.Tag

object RouteTag {
    val UserApplication = Tag("User Application", "Related to manage user applications, may involved some permissions.")
    val Authorization = Tag("Authorization", "Login, logout, etc.")
    val Registration = Tag("Registration", "Anything consists the registration procedure.")

    val Self = Tag("Self", "Query or edit information of oneself.")
    val Users = Tag("Users", "Admin: list, modify, delete user(s).")
}