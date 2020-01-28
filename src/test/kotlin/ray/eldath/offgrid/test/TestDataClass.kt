package ray.eldath.offgrid.test

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.Permission.*
import ray.eldath.offgrid.util.UserRole
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo

class TestDataClass {

    @Nested
    inner class TestPermission {

        @Test
        fun `test single permission expand`() {
            expectThat(Permission.expand(ApproveUserApplication))
                .isEqualTo(listOf(ApproveUserApplication))
        }

        @Test
        fun `test Root permission expand`() {
            expectThat(Permission.expand(Root))
                .containsExactlyInAnyOrder(values().toList())
        }

        @Test
        fun `test ModelRegistry expand`() {
            expectThat(Permission.expand(ModelRegistry))
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
}