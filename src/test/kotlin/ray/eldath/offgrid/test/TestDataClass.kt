package ray.eldath.offgrid.test

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ray.eldath.offgrid.component.InboundUser
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.util.ErrorCodes.permissionDenied
import ray.eldath.offgrid.util.Permission
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
            expectThat(ApproveUserApplication.expand())
                .containsExactlyInAnyOrder(ApproveUserApplication)
        }

        @Test
        fun `test Root permission expand`() {
            expectThat(Root.expand())
                .containsExactlyInAnyOrder(values().toList())
        }

        @Test
        fun `test ModelRegistry expand`() {
            expectThat(ModelRegistry.expand())
                .containsExactlyInAnyOrder(
                    ModelRegistry,
                    ListModelRegistry,
                    CreateModelRegistry,
                    UpdateModelRegistry,
                    DeleteModelRegistry
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
        private val user =
            ray.eldath.offgrid.generated.offgrid.tables.pojos.User(1, "Ray Eldath", "ray.eldath@aol.com")

        @Test
        fun `test failed requirePermission`() {
            val auth = Authorization(1, "", UserRole.PlatformAdmin)
            val inbound = InboundUser(user, auth, listOf(ExtraPermission(1, User, true)))

            expectCatching { inbound.requirePermission(CreateUser) }.failed()
                .isEqualTo(permissionDeniedException(CreateUser)).println()

            expectCatching { inbound.requirePermission(User, DeleteUser) }.failed()
                .isEqualTo(permissionDeniedException(User, DeleteUser)).println()

            expectCatching { inbound.requirePermission(ComputationResult) }.failed()
                .isEqualTo(permissionDeniedException(ComputationResult)).println()
        }

        @Test
        fun `test success requirePermission`() {
            val auth = Authorization(1, "", UserRole.MetricsAdmin)
            val inbound = InboundUser(user, auth, listOf(ExtraPermission(1, User, false)))

            expectCatching { inbound.requirePermission(CreateUser, DeleteUser, User) }.succeeded().println()
            expectCatching { inbound.requirePermission(Metrics, SystemMetrics) }.succeeded().println()
        }

        private fun permissionDeniedException(vararg require: Permission) =
            permissionDenied(require.expand().toList())()
    }
}