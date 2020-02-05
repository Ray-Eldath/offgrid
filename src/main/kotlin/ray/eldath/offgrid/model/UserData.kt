package ray.eldath.offgrid.model

import ray.eldath.offgrid.util.ErrorCodes

data class UsernamePassword(val username: String, val password: String) {
    companion object {
        private const val MAX_USERNAME_LENGTH = 16
        private const val MAX_PASSWORD_LENGTH = 18
        private const val MIN_PASSWORD_LENGTH = 6

        fun checkPassword(p: String): Unit =
            p.length.let {
                if (it <= MIN_PASSWORD_LENGTH)
                    throw ErrorCodes.InvalidRegisterSubmission.PASSWORD_TOO_SHORT()
                if (it > MAX_PASSWORD_LENGTH)
                    throw ErrorCodes.InvalidRegisterSubmission.PASSWORD_TOO_LONG()
            }

        fun checkUsername(u: String): Unit =
            u.length.let {
                if (it > MAX_USERNAME_LENGTH)
                    throw ErrorCodes.InvalidRegisterSubmission.USERNAME_TOO_LONG()
            }
    }

    fun check() {
        checkPassword(password)
        checkUsername(username)
    }
}