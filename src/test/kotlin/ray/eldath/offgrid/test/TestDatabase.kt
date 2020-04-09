package ray.eldath.offgrid.test

import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import ray.eldath.offgrid.component.UserRegistrationStatus
import ray.eldath.offgrid.core.Core.enableDebug
import ray.eldath.offgrid.core.Core.jooqContext
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions.EXTRA_PERMISSIONS
import ray.eldath.offgrid.generated.offgrid.tables.Users.USERS
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.util.Permission.*
import ray.eldath.offgrid.util.UserRole
import ray.eldath.offgrid.util.transaction
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import java.time.LocalDateTime

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
        transaction {
            val user = newRecord(USERS).apply {
                username = "offgrid test"
                email = "test@offgrid.ray-eldath.me"
                role = UserRole.Root
                hashedPassword = Context.hashedPassword
                registerTime = LocalDateTime.now()
            }
            user.store()

            val executed = user.id
            println("inserted userId: $executed")

            val extra1 = newRecord(EXTRA_PERMISSIONS).apply {
                userId = executed
                permissionId = ComputationResult
                isShield = true
            }

            val extra2 = newRecord(EXTRA_PERMISSIONS).apply {
                userId = executed
                permissionId = SelfComputationResult
                isShield = false
            }

            expectThat(extra1.insert()).isEqualTo(1)
            expectThat(extra2.insert()).isEqualTo(1)
        }
        println("insert test data successfully")
    }

    @Test
    fun `select User join Authorization and ExtraPermission`() {
        val status = UserRegistrationStatus.fetchByEmail("test@offgrid.ray-eldath.me")

        expect {
            that(status).isA<UserRegistrationStatus.Registered>().get { inbound }

            val inbound = (status as UserRegistrationStatus.Registered).inbound
            that(inbound) {
                val (user, permissions) = inbound
                val userId = user.id

                that(user.email).isEqualTo("test@offgrid.ray-eldath.me")
                that(user.role).isEqualTo(UserRole.Root)
                that(permissions).containsExactlyInAnyOrder(
                    ExtraPermission(userId, ComputationResult, true),
                    ExtraPermission(userId, SelfComputationResult, false)
                )

                catching { inbound.requirePermission(ModifyUser) }.succeeded()
                catching { inbound.requirePermission(SelfComputationResult) }.succeeded()
                catching { inbound.requirePermission(ComputationResult) }.failed().println()
            }
        }
    }

    @AfterAll
    fun `delete test data`() {
        transaction {
            delete(USERS)
                .where(USERS.USERNAME.eq("offgrid test")).execute()
        }
        println("delete test data successfully")
    }
}