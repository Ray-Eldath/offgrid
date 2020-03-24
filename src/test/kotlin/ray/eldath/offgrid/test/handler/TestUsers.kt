package ray.eldath.offgrid.test.handler

import org.http4k.core.HttpMessage
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.with
import org.junit.jupiter.api.*
import ray.eldath.offgrid.core.Core.credentials
import ray.eldath.offgrid.core.Core.security
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.records.UsersRecord
import ray.eldath.offgrid.handler.ListUsers
import ray.eldath.offgrid.handler.Login
import ray.eldath.offgrid.test.Context
import ray.eldath.offgrid.test.TestDatabase
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.UserRole
import ray.eldath.offgrid.util.transaction
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import java.net.URLEncoder
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestUsers {
    @Nested
    inner class TestListUsers {
        private val listUsers = ListUsers(credentials, security).compile()
        private val resp = { message: HttpMessage ->
            ListUsers.responseLens(message).result.also { println(it) }.map { it.username }.expect()
        }

        @Test
        fun `should return empty`() {
            resp(listUsers(request("username" to "wwww"))).isEmpty()

            resp(listUsers(request("email" to "omega.beta"))).isEmpty()
        }

        @Test
        fun `list by username fuzzily`() {
            resp(listUsers(request("username" to "ww")))
                .containsExactlyInAnyOrder("www")
        }

        @Test
        fun `list by email fuzzily`() {
            resp(listUsers(request("email" to "alpha.com")))
                .containsExactlyInAnyOrder("offgrid test", "bar foo")
        }

        @Test
        fun `list by email fuzzily and role`() {
            resp(listUsers(request("email" to "alpha.com", "role" to UserRole.MetricsAdmin.id.toString())))
                .containsExactlyInAnyOrder("bar foo")
        }

        @Test
        fun `list by permission`() {
            resp(listUsers(request("permission" to Permission.RejectUserApplication.id)))
                .containsExactlyInAnyOrder("offgrid test", "bar foo")

            resp(listUsers(request("permission" to Permission.PanelMetrics.id)))
                .containsExactlyInAnyOrder("offgrid test", "foo bar", "bar foo")

            resp(listUsers(request("permission" to Permission.User.id)))
                .containsExactlyInAnyOrder("offgrid test", "www")
        }

        private fun request(vararg queries: Pair<String, String>) =
            queries.joinToString("&") { (key, value) ->
                URLEncoder.encode(value, Charsets.UTF_8).let { "$key=$it" }
            }.let { Request(Method.GET, "/users" + if (queries.isNotEmpty()) "?$it" else "").auth() }
    }

    @Test
    @Order(2)
    fun `login by root`() {
        rootBearer
    }

    private val usernameEmail = listOf(
        "offgrid test" to "omega@alpha.com",
        "foo bar" to "alpha@omega.com",
        "bar foo" to "beta@alpha.com",
        "www" to "alpha@beta.com"
    )
    private val roles = listOf(UserRole.Root, UserRole.MetricsAdmin, UserRole.MetricsAdmin, UserRole.PlatformAdmin)
    private val permissions = listOf(
        listOf(),
        listOf(),
        listOf(Permission.UserApplication to false, Permission.SelfComputationResult to false),
        listOf(Permission.RejectUserApplication to true)
    )
    private val users = arrayListOf<UsersRecord>()
    private val rootBearer by lazy {
        Login.responseLens(
            Login().compile()(
                Request(Method.POST, "/login")
                    .with(Login.requestLens of Login.LoginRequest("omega@alpha.com", "123"))
            )
        ).bearer
    }

    @Test
    @Order(1) // lower means higher priority
    fun `insert whole data`() {
        assert(usernameEmail.size == roles.size)
        assert(usernameEmail.size == permissions.size)

        transaction {
            val u = Users.USERS
            val ep = ExtraPermissions.EXTRA_PERMISSIONS

            usernameEmail.mapIndexedTo(users) { i, (un, e) ->
                newRecord(u).apply {
                    username = un
                    email = e
                    hashedPassword = Context.hashedPassword
                    role = roles[i]
                    registerTime = LocalDateTime.now()
                }
            }.forEach { it.insert() } // shouldn't use batchInsert here.

            permissions.mapIndexed { i, p ->
                if (p.isNotEmpty())
                    p.map {
                        newRecord(ep).apply {
                            userId = users[i].id
                            permissionId = it.first
                            isShield = it.second
                        }
                    }
                else null
            }.filterNotNull().flatten()
                .forEach { it.insert() }
        }
    }

    @BeforeAll
    fun `prepare database`() {
        TestDatabase.`prepare database`()
    }

    @AfterAll
    fun cleanup() {
        transaction {
            batchDelete(users).execute()
        }
    }

    private fun Request.auth() = header("Authorization", "Bearer $rootBearer")
    private fun <T> T.expect() = expectThat(this)
}