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
import ray.eldath.offgrid.util.UserRole
import ray.eldath.offgrid.util.transaction

class ListUsers(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class ListResponseRole(val id: Int, val name: String)
    data class ListResponseEntry(val id: Int, val username: String, val role: ListResponseRole)
    data class ListResponse(val result: List<ListResponseEntry>)

    private val pageLens = Query.int().defaulted("page", 1, "the n-th page of result")
    private val pageSizeLens =
        Query.int().defaulted("pre_page", 20, "the size of elements that one page should contain.")

    private val idLens = Query.int().optional("id", "Exact search by id")
    private val emailLens = Query.string().optional("email", "Fuzzy filtered by email")
    private val usernameLens = Query.string().optional("username", "Fuzzy filtered by username")
    private val roleLens = Query.int().optional("role", "Filter by user role")
    private val permissionsLens = Query.string().optional("permissions", "Filter by permission(s)")

    private val handler: HttpHandler = { req ->
        credentials(req).requirePermission(Permission.ListUser)

        val page = pageLens(req)
        val pageSize = pageSizeLens(req)

        val id = idLens(req)
        val email = emailLens(req)
        val username = usernameLens(req)
        val role = roleLens(req)?.let { UserRole.fromId(it) }
        val permissions =
            permissionsLens(req)
                ?.split(",")
                ?.mapNotNull { Permission.fromId(it) }
                ?.ifEmpty { null }

        val permissionsRoles = permissions?.flatMap { UserRole.fromPermission(it) }?.toHashSet()

        transaction<MutableMap<User, MutableList<Authorization>>?> {
            val u = Users.USERS
            val a = Authorizations.AUTHORIZATIONS
            val ep = ExtraPermissions.EXTRA_PERMISSIONS

            select()
                .from(u)
                .innerJoin(a).onKey()
                .apply {
                    if (!allNull(id, email, username, role, permissions)) {
                        if (id != null)
                            where(u.ID.eq(id))
                        if (email != null)
                            where(u.EMAIL.likeIgnoreCase("%$email%"))
                        if (username != null)
                            where(u.USERNAME.likeIgnoreCase("%$username%"))
                        if (role != null) {
                            if (permissionsRoles == null)
                                where(a.ROLE.eq(role))
                            else
                                where(a.ROLE.`in`(permissionsRoles.also { it.add(role) }))
                        }
                        if (permissions != null) {
                            whereExists(
                                select()
                                    .from(ep)
                                    .where(
                                        ep.AUTHORIZATION_ID.eq(a.USER_ID)
                                            .and(
                                                ep.PERMISSION_ID.`in`(permissions)
                                                    .and(ep.IS_SHIELD.isFalse)
                                            )
                                    )
                            )

                            whereNotExists(
                                select()
                                    .from(ep)
                                    .where(
                                        ep.AUTHORIZATION_ID.eq(a.USER_ID)
                                            .and(
                                                ep.PERMISSION_ID.`in`(permissions)
                                                    .and(ep.IS_SHIELD.isTrue)
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
            description = "provide pagination and filter parameters for matched users, but none of them is required."

            queries += listOf(pageLens, pageSizeLens, idLens, emailLens, usernameLens, roleLens, permissionsLens)

            produces += ContentType.APPLICATION_JSON
            returning(
                Status.OK,
                responseLens to ListResponse(
                    listOf(
                        ListResponseEntry(30213, "",
                            UserRole.Root.let { ListResponseRole(it.id, it.name) })
                    )
                )
            )
        } bindContract Method.GET to handler

    companion object {
        val responseLens = Body.auto<ListResponse>().toLens()
    }
}