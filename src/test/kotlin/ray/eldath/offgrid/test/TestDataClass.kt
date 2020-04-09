package ray.eldath.offgrid.test

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ray.eldath.offgrid.component.InboundUser
import ray.eldath.offgrid.component.UserRegistrationStatus
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.handler.Login
import ray.eldath.offgrid.test.Context.application
import ray.eldath.offgrid.test.Context.inbound
import ray.eldath.offgrid.test.Context.password
import ray.eldath.offgrid.test.Context.user
import ray.eldath.offgrid.test.Context.wrongPassword
import ray.eldath.offgrid.util.ErrorCodes.APPLICATION_PENDING
import ray.eldath.offgrid.util.ErrorCodes.APPLICATION_REJECTED
import ray.eldath.offgrid.util.ErrorCodes.UNCONFIRMED_EMAIL
import ray.eldath.offgrid.util.ErrorCodes.USER_NOT_FOUND
import ray.eldath.offgrid.util.Permission.*
import ray.eldath.offgrid.util.Permission.Companion.expand
import ray.eldath.offgrid.util.UserRole
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.failed
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

class TestDataClass {

    @Nested
    inner class TestPermission {

        @Test
        fun `test single permission expand`() {
            expectThat(ListUserApplication.expand().toList())
                .containsExactlyInAnyOrder(ListUserApplication)
        }

        @Test
        fun `test Root permission expand`() {
            expectThat(Root.expand().toList())
                .containsExactlyInAnyOrder(values().toList())
        }

        @Test
        fun `test ModelRegistry expand`() {
            expectThat(Consumer.expand().toList())
                .containsExactlyInAnyOrder(
                    Consumer,
                    ListConsumer,
                    CreateConsumer,
                    ModifyConsumer,
                    DeleteConsumer
                )
        }
    }

    @Nested
    inner class TestUserRole {

        @Test
        fun `test Root role permissions`() {
            expectThat(UserRole.Root.defaultPermissions)
                .containsExactlyInAnyOrder(values().toList())
        }
    }

    @Nested
    inner class TestInboundUser {
        @Test
        fun `test failed requirePermission`() {
            expectCatching { inbound.requirePermission(ListUser) }.failed()
                .isEqualTo(permissionDeniedException(ListUser)).println()

            expectCatching { inbound.requirePermission(User, DeleteUser) }.failed()
                .isEqualTo(permissionDeniedException(User, DeleteUser)).println()

            expectCatching { inbound.requirePermission(ComputationResult) }.failed()
                .isEqualTo(permissionDeniedException(ComputationResult)).println()
        }

        @Test
        fun `test success requirePermission`() {
            val testingUser = user.run {
                ray.eldath.offgrid.generated.offgrid.tables.pojos.User(
                    id,
                    state,
                    email,
                    username,
                    hashedPassword,
                    UserRole.MetricsAdmin,
                    lastLoginTime,
                    registerTime
                )
            }

            val inbound = InboundUser(testingUser, listOf(ExtraPermission(1, User, false)))

            expectCatching { inbound.requirePermission(ListUser, DeleteUser, User) }.succeeded().println()
            expectCatching { inbound.requirePermission(Metrics, PanelMetrics) }.succeeded().println()
        }
    }

    @Nested
    inner class TestUserRegistrationStatus {

        @Test
        fun `test pure UserRegistrationStatus`() {
            expectThat(Login.runState(UserRegistrationStatus.NotFound, password)).get { first }
                .isEqualTo(USER_NOT_FOUND)

            expectThat(Login.runState(UserRegistrationStatus.Registered(inbound), wrongPassword)).get { first }
                .isEqualTo(USER_NOT_FOUND)
        }

        @Test
        fun `test with UserApplication`() {
            expectThat(Login.runState(UserRegistrationStatus.Unconfirmed(application))).get { first }
                .isEqualTo(UNCONFIRMED_EMAIL)
            expectThat(Login.runState(UserRegistrationStatus.ApplicationPending(application))).get { first }
                .isEqualTo(APPLICATION_PENDING)
            expectThat(Login.runState(UserRegistrationStatus.ApplicationRejected(application))).get { first }
                .isEqualTo(APPLICATION_REJECTED)
        }

        @Test
        fun `test success with InboundUser`() {
            expectThat(Login.runState(UserRegistrationStatus.Registered(inbound), password))
                .isEqualTo(null to UserRegistrationStatus.Registered(inbound))
        }
    }
}