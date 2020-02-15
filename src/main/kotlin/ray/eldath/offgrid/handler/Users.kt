package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.jooq.Condition
import ray.eldath.offgrid.generated.offgrid.tables.Authorizations
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.handler.UsersHandler.checkUserId
import ray.eldath.offgrid.model.*
import ray.eldath.offgrid.util.*

class ListUsers(credentials: Credentials, optionalSecurity: Security) : ContractHandler(credentials, optionalSecurity) {
    data class ListResponseEntry(val id: Int, val username: String, val email: String, val role: OutboundRole)
    data class ListResponse(val total: Int, val result: List<ListResponseEntry>)

    private val pageLens = Query.int().defaulted("page", 1, "the n-th page of result")
    private val pageSizeLens =
        Query.int().defaulted("pre_page", 20, "the size of elements that one page should contain.")

    private val idLens = Query.int().optional("id", "exact search by id")
    private val emailLens = Query.string().optional("email", "fuzzily filter by email")
    private val usernameLens = Query.string().optional("username", "fuzzily filter by username")
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

        transaction {
            val u = Users.USERS
            val a = Authorizations.AUTHORIZATIONS
            val ep = ExtraPermissions.EXTRA_PERMISSIONS

            val conditions = arrayListOf<Condition>().also {
                if (id != null)
                    it += u.ID.eq(id)
                if (email != null)
                    it += u.EMAIL.likeIgnoreCase("%$email%")
                if (username != null)
                    it += u.USERNAME.likeIgnoreCase("%$username%")
                if (role != null)
                    it +=
                        if (permissionRoles == null)
                            a.ROLE.eq(role)
                        else
                            a.ROLE.`in`(permissionRoles.also { r -> r.add(role) })
                if (permission != null)
                    it +=
                        a.ROLE.`in`(permissionRoles).and(
                            ep.PERMISSION_ID.isNull.orNot(
                                ep.PERMISSION_ID.eq(permission).and(ep.IS_SHIELD.isTrue)
                            )
                        ).or(
                            ep.PERMISSION_ID.`in`(Permission.fromId(permission.rootId), permission).and(
                                ep.IS_SHIELD.isFalse
                            )
                        )
            }

            val prefix = selectDistinct(u.EMAIL)
                .from(u)
                .innerJoin(a).on(u.ID.eq(a.USER_ID))
                .leftJoin(ep).on(ep.AUTHORIZATION_ID.eq(a.USER_ID))
                .where(conditions)

            ListResponse(
                total = fetchCount(prefix),
                result = prefix.orderBy(u.ID).limit(pageSize).offset((page - 1) * pageSize)
                    .fetchGroups(
                        { it.into(u).into(User::class.java) },
                        { it.into(a).into(Authorization::class.java) })
                    ?.filterValues { it.size == 1 }
                    ?.mapValues { it.value[0] }
                    .orEmpty()
                    .map { (user, auth) ->
                        ListResponseEntry(
                            user.id,
                            user.username,
                            user.email,
                            auth.role.toOutbound()
                        )
                    }
            )
        }.let { Response(Status.OK).with(responseLens of it) }
    }

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
                    1,
                    listOf(ListResponseEntry(30213, "Ray Eldath", "abc@omega.com", UserRole.Root.toOutbound()))
                )
            )
        } bindContract Method.GET to handler

    companion object {
        val responseLens = Body.auto<ListResponse>().toLens()
    }
}

class ModifyUser(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class UpdateRequest(
        val username: String?,
        override val email: String?,
        val role: Int?,
        val extraPermissions: List<InboundExtraPermission>?
    ) : EmailRequest(email)

    private fun handler(userId: Int): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.ModifyUser)
        checkUserId(userId)

        val json = requestLens(req)

        transaction {
            val u = Users.USERS
            val a = Authorizations.AUTHORIZATIONS
            val ep = ExtraPermissions.EXTRA_PERMISSIONS

            if (json.username != null || json.email != null)
                newRecord(u).apply {
                    id = userId
                    if (json.username != null)
                        username = json.username
                    if (json.email != null)
                        email = json.email
                }.update()

            if (json.role != null)
                newRecord(a).apply {
                    this.userId = userId
                    role = UserRole.fromId(json.role)
                }.update()

            if (json.extraPermissions != null) {
                deleteFrom(ep)
                    .where(ep.AUTHORIZATION_ID.eq(userId)).execute()

                json.extraPermissions.map {
                    newRecord(ep).apply {
                        authorizationId = userId
                        permissionId = Permission.fromId(it.id)
                        isShield = it.isShield
                    }
                }.let { batchInsert(it) }
            }
        }

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/users" / Path.int().of("id", "id of the user") meta {
            summary = "Edit user"
            description =
                "All fields are optional. Note that any modification will only take effect after the modified user is re-login."
            tags += RouteTag.Users

            security = optionalSecurity
            consumes += ContentType.APPLICATION_JSON
            receiving(
                requestLens to UpdateRequest(
                    "Ray Edas",
                    "alpha.beta@omega.com",
                    UserRole.UserAdmin.id,
                    listOf(
                        Permission.ListUser.toExtraExchangeable(false),
                        Permission.DeleteUser.toExtraExchangeable(true)
                    )
                )
            )
            returning(Status.OK to "specified user has been updated use given information.")
        } bindContract Method.PATCH to ::handler

    companion object {
        private val requestLens = Body.auto<UpdateRequest>().toLens()
    }
}

class DeleteUser(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    private fun handler(userId: Int): HttpHandler = {
        credentials(it).requirePermission(Permission.DeleteUser)
        checkUserId(userId)
        deleteUser(userId)

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/users" / Path.int().of("id", "id of the user") meta {
            summary = "Delete user"
            tags += RouteTag.Users

            security = optionalSecurity
            consumes += ContentType.APPLICATION_JSON
            returning(Status.OK to "specified user has been deleted.")
        } bindContract Method.DELETE to ::handler

    companion object {
        fun deleteUser(userId: Int) {
            transaction {
                val u = Users.USERS
                delete(u).where(u.ID.eq(userId)) // cascade deletion
            }
        }
    }
}

object UsersHandler {

    fun checkUserId(userId: Int) {
        transaction {
            val u = Users.USERS
            select()
                .from(u)
                .where(u.ID.eq(userId))
                .fetchOptional().orElseThrow { ErrorCodes.commonNotFound()() }
        }
    }
}