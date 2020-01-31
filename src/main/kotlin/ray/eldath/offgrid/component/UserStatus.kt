package ray.eldath.offgrid.component

import ray.eldath.offgrid.generated.offgrid.tables.Authorizations
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.UserApplications
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.util.transaction
import java.util.*

enum class UserStatus(val code: Int) {
    @Deprecated("left side of either just remains unset to indicate successful result")
    AUTHORIZED(1),
    NOT_FOUND(2),
    UNCONFIRMED(3),
    APPLICATION_PENDING(10),
    APPLICATION_REJECTED(11);

    companion object {
        fun fetchByEmail(email: String): Either<UserStatus, InboundUser> {
            val inbound = fetchInboundUser(email)
            if (inbound.isPresent)
                return inbound.get().toRight()
            return transaction {
                val ua = UserApplications.USER_APPLICATIONS
                val applicationOptional = select()
                    .from(ua)
                    .where(ua.EMAIL.eq(email))
                    .fetchOptional { it.into(ua).into(UserApplication::class.java) }
                if (applicationOptional.isEmpty)
                    NOT_FOUND.toLeft()
                else {
                    val application = applicationOptional.get()

                    if (application.isEmailConfirmed == false)
                        UNCONFIRMED.toLeft()
                    else {
                        if (application.isApplicationPending)
                            APPLICATION_PENDING.toLeft()
                        else APPLICATION_REJECTED.toLeft()
                    }
                }
            }
        }

        private fun fetchInboundUser(email: String): Optional<InboundUser> =
            transaction {
                val u = Users.USERS
                val a = Authorizations.AUTHORIZATIONS
                val e = ExtraPermissions.EXTRA_PERMISSIONS

                val optionalUser = select()
                    .from(u)
                    .innerJoin(a).onKey()
                    .where(u.EMAIL.eq(email))
                    .fetchOptional {
                        Pair(
                            it.into(u).into(User::class.java),
                            it.into(a).into(Authorization::class.java)
                        )
                    }
                if (optionalUser.isEmpty)
                    Optional.empty()
                else {
                    val (user, auth) = optionalUser.get()

                    val list = select()
                        .from(e)
                        .innerJoin(a).on(e.AUTHORIZATION_ID.eq(a.USER_ID))
                        .fetch { it.into(e).into(ExtraPermission::class.java) }

                    Optional.of(InboundUser(user, auth, list))
                }
            }
    }
}

data class Either<out L, out R>(val left: L?, val right: R?) {

    val leftOrThrow: L
        get() = left ?: throw NullPointerException("left value of $this is unset")
    val rightOrThrow: R
        get() = right ?: throw NullPointerException("right value of $this is unset")

    val haveLeft: Boolean
        get() = left != null
    val haveRight: Boolean
        get() = right != null
}

fun <L> L.toLeft() = Either(this, null)
fun <R> R.toRight() = Either(null, this)
fun <L> Optional<L>.toLeft() = this.get().toLeft()
fun <R> Optional<R>.toRight() = this.get().toRight()
fun <L, R> Pair<L, R>.toEither() = Either(this.first, this.second)