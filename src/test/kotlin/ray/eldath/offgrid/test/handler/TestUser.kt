package ray.eldath.offgrid.test.handler

import org.http4k.core.Status
import org.http4k.core.with
import org.junit.jupiter.api.*
import ray.eldath.offgrid.core.Core.credentials
import ray.eldath.offgrid.core.Core.security
import ray.eldath.offgrid.handler.Login
import ray.eldath.offgrid.handler.Logout
import ray.eldath.offgrid.test.*
import ray.eldath.offgrid.util.ErrorCodes
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.failed
import strikt.assertions.isEqualTo
import java.util.*

@ExperimentalStdlibApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestUser {
    @Nested
    inner class TestLogin {
        private fun login(email: String, password: String) =
            Login(credentials, security).compile()
                .invoke("/login".POST().with(Login.requestLens of Login.LoginRequest(email, password)))

        @Test
        fun `malformed email address`() {
            expectCatching { login("test@offgrid", "") }.failed()
                .isEqualTo(ErrorCodes.INVALID_EMAIL_ADDRESS()).println()
        }

        @Test
        fun `wrong user email address`() {
            expectCatching { login("test@offgrid.org", "123") }.failed()
                .isEqualTo(ErrorCodes.USER_NOT_FOUND()).println()
        }

        @Test
        fun `wrong user password`() {
            expectCatching { login("test@offgrid.ray-eldath.me", "1234") }.failed()
                .isEqualTo(ErrorCodes.USER_NOT_FOUND()).println()
        }

        @Test
        fun `test success`() {
            expectThat(success()).println().status()
                .isEqualTo(Status.OK)
        }

        fun success() = login("test@offgrid.ray-eldath.me", "123")
    }

    @Nested
    inner class TestLogout {

        @Test
        fun `test Security`() {
            expectCatching { logout(UUID.randomUUID().toString()) }.failed()
                .isEqualTo(ErrorCodes.LOGIN_REQUIRED()).println()
        }

        @Test
        fun `test success`() {
            val login = TestLogin().success()
            expectThat(logout(login.json()["bearer"].asText())).println().status()
                .isEqualTo(Status.OK)
        }

        private val route = Logout(credentials, security).compile()

        private fun logout(bearer: String) =
            route.invoke("/logout".GET().header("Authorization", "Bearer $bearer"))
    }

    @BeforeAll
    fun `prepare test data`() {
        TestDatabase.`prepare database`()
        TestDatabase.`insert test data`()
    }

    @AfterAll
    fun `delete test data`() {
        TestDatabase.`delete test data`()
    }
}