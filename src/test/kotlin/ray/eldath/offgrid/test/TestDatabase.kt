package ray.eldath.offgrid.test

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import ray.eldath.offgrid.core.Core.enableDebug
import ray.eldath.offgrid.core.Core.jooqContext
import ray.eldath.offgrid.generated.offgrid.tables.Authorizations.AUTHORIZATIONS
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions.EXTRA_PERMISSIONS
import ray.eldath.offgrid.generated.offgrid.tables.Users.USERS
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.UserRole
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
object TestDatabase {

    @BeforeAll
    fun `prepare database`() {
        enableDebug
        jooqContext
    }

    @Test
    @Order(1) // lower means higher priority
    fun `insert test data`() {
        val hk =
            "\$argon2i\$v=19\$m=65536,t=10,p=1\$sOTA4jpvIiEfrIl6qacjcA\$6BcaWsQNTPCHT1f0kRQeEm3NmT8yAN8UMJJs5oczD70"
        transaction {
            val user = newRecord(USERS).apply {
                username = "Ray Eldath"
                email = "alpha@beta.omega"
                isEmailConfirmed = true
            }
            user.store()
            val executed = user.id
            println("inserted userId: $executed")

            val auth = newRecord(AUTHORIZATIONS).apply {
                userId = executed
                role = UserRole.Root
                setPasswordHashed(*(hk.toByteArray()))
            }
            auth.insert()
            println("inserted authId: ${auth.userId}")

            expectThat(auth.userId).isEqualTo(executed)

            val extra = newRecord(EXTRA_PERMISSIONS).apply {
                authorizationId = auth.userId
                permissionId = Permission.ComputationResult
                isShield = true
            }

            expectThat(extra.authorizationId).isEqualTo(auth.userId)
        }
    }

    @Test
    fun `select User joining Authorization`() {
        val (email, confirmed, role) = transaction {
            val u = USERS
            val a = AUTHORIZATIONS

            select(u.EMAIL, u.IS_EMAIL_CONFIRMED, a.ROLE)
                .from(u)
                .innerJoin(a).onKey()
                .single()
        }
        expect {
            that(email).isEqualTo("alpha@beta.omega")
            that(confirmed).isTrue()
            that(role).isEqualTo(UserRole.Root)
        }
    }

    @AfterAll
    fun `delete test data`() {
        transaction {
            delete(USERS)
                .where(USERS.USERNAME.eq("Ray Eldath"))
        }
    }

    private fun <T> transaction(context: DSLContext = jooqContext, block: DSLContext.() -> T): T {
        var a: T? = null
        context.transaction { cfg ->
            a = DSL.using(cfg).block()
        }
        return a!!
    }
}