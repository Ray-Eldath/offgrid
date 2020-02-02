package ray.eldath.offgrid.test

import ray.eldath.offgrid.component.InboundUser
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.UserRole
import java.sql.Timestamp

object Context {
    val wrongPassword = "1234".toByteArray()
    val password = "123".toByteArray()
    val hashedPassword =
        "\$argon2i\$v=19\$m=65536,t=10,p=1\$sOTA4jpvIiEfrIl6qacjcA\$6BcaWsQNTPCHT1f0kRQeEm3NmT8yAN8UMJJs5oczD70" // 123

    val user = User(1, "Ray Eldath", "ray.eldath@aol.com")
    val auth = Authorization(1, hashedPassword, UserRole.PlatformAdmin)
    val inbound = InboundUser(user, auth, listOf(ExtraPermission(1, Permission.User, true)))

    val application =
        UserApplication(
            1,
            "alpha@beta.omega",
            true,
            "",
            Timestamp(123), // TODO:
            hashedPassword,
            "Ray Eldath",
            true
        )
}