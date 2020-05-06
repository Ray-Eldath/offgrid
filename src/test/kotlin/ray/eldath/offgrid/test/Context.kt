package ray.eldath.offgrid.test

import org.http4k.contract.security.Security
import org.http4k.core.Filter
import org.http4k.lens.Meta
import org.http4k.lens.ParamMeta
import org.http4k.lens.RequestContextLens
import ray.eldath.offgrid.component.InboundUser
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.UserRole
import ray.eldath.offgrid.util.UserState
import java.time.LocalDateTime

object Context {
    val wrongPassword = "1234".toByteArray()
    val password = "123".toByteArray()
    const val hashedPassword =
        "\$argon2i\$v=19\$m=65536,t=10,p=1\$sOTA4jpvIiEfrIl6qacjcA\$6BcaWsQNTPCHT1f0kRQeEm3NmT8yAN8UMJJs5oczD70" // 123

    val user = User(
        1,
        UserState.Normal,
        "ray.eldath@aol.com",
        "offgrid test",
        hashedPassword,
        UserRole.PlatformAdmin,
        LocalDateTime.now().minusMinutes(42),
        LocalDateTime.now().minusWeeks(6)
    )
    val inbound = InboundUser(user, listOf(ExtraPermission(1, Permission.User, true)))
    val inboundRoot = InboundUser(user)

    val application =
        UserApplication(
            1,
            "test@offgrid.ray-eldath.me",
            true,
            "",
            LocalDateTime.now(),
            hashedPassword,
            "offgrid test",
            true
        )

    object MockSecurity : Security {
        val mockCredentials =
            RequestContextLens(
                Meta(false, "", ParamMeta.NullParam, ""),
                { inboundRoot },
                { _, req -> req }
            )

        override val filter: Filter = Filter { it }
    }
}