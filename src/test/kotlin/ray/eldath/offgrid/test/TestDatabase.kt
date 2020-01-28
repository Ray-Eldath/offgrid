package ray.eldath.offgrid.test

import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import ray.eldath.offgrid.core.Core.prepareDatabase
import ray.eldath.offgrid.dao.Authorization
import ray.eldath.offgrid.dao.User
import ray.eldath.offgrid.util.UserRole
import ray.eldath.offgrid.util.toBlob

@TestInstance(Lifecycle.PER_CLASS)
object TestDatabase {

    @Test
    @BeforeAll
    fun `test prepare database`() {
        prepareDatabase(true)
    }

    @Test
    fun `insert test data`() {
        val hp =
            "\$argon2i\$v=19\$m=65536,t=10,p=1\$JAqVeeNXYjPzqQxYOuek9w\$bV2xPVMnPsV36LesGvM/yUDaisR3Bugn9wneiCPMnBY"


        val result = transaction {
            addLogger(StdOutSqlLogger)

            val created = User.new {
                username = "Ray Eldath"
                email = "ray.eldath@outlook.com"
            }

            Authorization.new {
                user = created
                hashedPassword = hp.toBlob()
                role = UserRole.Root
            }
        }

        println(result)
    }
}