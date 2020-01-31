package ray.eldath.offgrid.component

import ray.eldath.offgrid.generated.offgrid.tables.Authorizations
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.UserApplications
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.Authorization
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.util.*
import java.util.*

typealias ApplicationOrInbound = Either<UserApplication, InboundUser>

enum class UserRegistrationStatus(val code: Int) {
    NOT_FOUND(2),
    UNCONFIRMED(3),
    APPLICATION_PENDING(10),
    APPLICATION_REJECTED(11);

    companion object {
        fun fetchByEmail(email: String): Either<UserRegistrationStatus, ApplicationOrInbound> {
            val inbound = fetchInboundUser(email)
            if (inbound.isPresent)
                return inbound.get().toRight().toRight()
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

                    val status =
                        if (application.isEmailConfirmed == false)
                            UNCONFIRMED
                        else {
                            if (application.isApplicationPending)
                                APPLICATION_PENDING
                            else APPLICATION_REJECTED
                        }

                    (status to (application.toLeft())).toEither()
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