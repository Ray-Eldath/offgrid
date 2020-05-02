package ray.eldath.offgrid.util

import org.http4k.contract.Tag

object RouteTag {
    val UserApplication = Tag("User Application", "Related to manage user applications, may involved some permissions.")
    val Authorization = Tag("Authorization", "Login, logout, etc.")
    val Registration = Tag("Registration", "Anything consists the registration procedure.")

    val ResetPassword = Tag("Request Password", "API consists procedure of resetting password.")
    val Self = Tag("Self", "Query or edit information of oneself.")

    val Users = Tag("Users", "Admin: list, modify, ban and unban, delete user(s).")

    val Hydra = Tag("Hydra", "Hydra Login & Consent Flow, should not be exposed to the public.")
    val Meta = Tag("Meta", "Metadata of the system, like all available user roles, permissions, etc.")
    val Debug = Tag("Debug", "Only for debug purpose, so only enabled in debug mode.")

    val DataSource = Tag(
        "DataSource", "Admin: list, create, modify, delete data source(s). \n" +
                "DataSource is the origin of fresh encrypted messages for Endpoint to compute."
    )
    val Endpoint = Tag(
        "Endpoint", "Admin: list, create, modify, delete endpoint(s). \n" +
                "Endpoint is where actual computation of fresh encrypted messages is done, " +
                "the DataSource-Endpoint connection is confined by Route."
    )
    val EntityTag = Tag(
        "Entity Tag", "Admin: get, create, delete tag(s) of an entity. " +
                "An Entity can be a datasource or an endpoint."
    )
}