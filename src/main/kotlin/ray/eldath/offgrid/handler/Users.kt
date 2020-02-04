package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import ray.eldath.offgrid.generated.offgrid.tables.Authorizations
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.RouteTag
import ray.eldath.offgrid.util.UserRole
import ray.eldath.offgrid.util.transaction

class ListUsers(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class ListResponseRole(val id: Int, val name: String)
    data class ListResponseEntry(val id: Int, val username: String, val role: ListResponseRole)
    data class ListResponse(val result: List<ListResponseEntry>)

    private val pageLens = Query.int().defaulted("page", 1, "the n-th page of result")
    private val pageSizeLens =
        Query.int().defaulted("pre_page", 20, "the size of elements that one page should contain.")

    private val idLens = Query.int().optional("id", "exact search by id")
    private val emailLens = Query.string().optional("email", "fuzzy filtered by email")
    private val usernameLens = Query.string().optional("username", "fuzzy filtered by username")
    private val roleLens = Query.int().optional("role", "filter by user role")
    private val permissionLens = Query.string().optional("permission", "filter by permission id")

    private val handler: HttpHandler = { req ->
        credentials(req).requirePermission(Permission.ListUser)

        val page = pageLens(req)
        val pageSize = pageSizeLens(req)

        val id = idLens(req)
        val email = emailLens(req)
        val username = usernameLens(req)
        val role = roleLens(req)?.let { UserRole.fromId(it) }

        val permissionPlain = permissionLens(req)
        val permission = permissionPlain?.let { Permission.fromId(it) }
        val permissionRoles = permission?.let { UserRole.fromPermission(it) }?.toMutableList()

        transaction<MutableMap<User, MutableList<Authorization>>?> {
            val u = Users.USERS
            val a = Authorizations.AUTHORIZATIONS
            val ep = ExtraPermissions.EXTRA_PERMISSIONS

            selectDistinct(u.fields().toMutableList().also { it.addAll(a.fields()) })
                .from(u)
                .innerJoin(a).on(u.ID.eq(a.USER_ID))
                .leftJoin(ep).on(ep.AUTHORIZATION_ID.eq(a.USER_ID))
                .where("true")
                .apply {
                    if (!allNull(id, email, username, role, permission)) {
                        if (id != null)
                            and(u.ID.eq(id))
                        if (email != null)
                            and(u.EMAIL.likeIgnoreCase("%$email%"))
                        if (username != null)
                            and(u.USERNAME.likeIgnoreCase("%$username%"))
                        if (role != null) {
                            if (permissionRoles == null)
                                and(a.ROLE.eq(role))
                            else
                                and(a.ROLE.`in`(permissionRoles.also { it.add(role) }))
                        }
                        if (permission != null) {
                            and(
                                a.ROLE.`in`(permissionRoles).and(
                                    ep.PERMISSION_ID.isNull.orNot(
                                        ep.PERMISSION_ID.eq(permission).and(ep.IS_SHIELD.isTrue)
                                    )
                                ).or(
                                    ep.PERMISSION_ID.`in`(Permission.fromId(permission.rootId), permission).and(
                                        ep.IS_SHIELD.isFalse
                                    )
                                )
                            )
                        }
                    }
                }.limit(pageSize).offset((page - 1) * pageSize)
                .fetchGroups(
                    { it.into(u).into(User::class.java) },
                    { it.into(a).into(Authorization::class.java) })
        }?.filterValues { it.size == 1 }
            ?.mapValues { it.value[0] }
            .orEmpty()
            .map { (user, auth) ->
                ListResponseEntry(
                    user.id,
                    user.username,
                    auth.role.let { ListResponseRole(it.id, it.name) })
            }
            .let { Response(Status.OK).with(responseLens of ListResponse(it)) }
    }

    private fun allNull(vararg elements: Any?) = elements.all { it == null }

    override fun compile(): ContractRoute =
        "/users" meta {
            summary = "Query users, ordered by id"
            description = "Provide pagination and filter parameters for matched users, but none of them is required."
            tags += RouteTag.Users
            security = optionalSecurity

            queries += listOf(pageLens, pageSizeLens, idLens, emailLens, usernameLens, roleLens, permissionLens)

            produces += ContentType.APPLICATION_JSON
            returning(
                Status.OK,
                responseLens to ListResponse(
                    listOf(
                        ListResponseEntry(30213, "Ray Eldath",
                            UserRole.Root.let { ListResponseRole(it.id, it.name) })
                    )
                )
            )
        } bindContract Method.GET to handler

    companion object {
        val responseLens = Body.auto<ListResponse>().toLens()
    }
}