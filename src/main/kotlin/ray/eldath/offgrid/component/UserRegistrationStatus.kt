package ray.eldath.offgrid.component

import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.UserApplications
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.ExtraPermission
import ray.eldath.offgrid.generated.offgrid.tables.pojos.User
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.util.transaction
import ray.eldath.offgrid.util.unsafeTransaction

sealed class UserRegistrationStatus {

    data class Registered(val inbound: InboundUser) : UserRegistrationStatus()
    object NotFound : UserRegistrationStatus()
    data class Unconfirmed(val application: UserApplication) : UserRegistrationStatus()
    data class ApplicationPending(val application: UserApplication) : UserRegistrationStatus()
    data class ApplicationRejected(val application: UserApplication) : UserRegistrationStatus()

    companion object {
        fun fetchByEmail(email: String): UserRegistrationStatus {
            val inbound = fetchInboundUser(email)
            if (inbound != null)
                return Registered(inbound)
            return transaction {
                val ua = UserApplications.USER_APPLICATIONS
                val applicationOptional = select()
                    .from(ua)
                    .where(ua.EMAIL.eq(email))
                    .fetchOptionalInto(UserApplication::class.java)

                if (applicationOptional.isEmpty)
                    NotFound
                else {
                    val application = applicationOptional.get()

                    if (application.isEmailConfirmed == false)
                        Unconfirmed(application)
                    else {
                        if (application.isApplicationPending)
                            ApplicationPending(application)
                        else ApplicationRejected(application)
                    }
                }
            }
        }

        private fun fetchInboundUser(email: String): InboundUser? =
            unsafeTransaction {
                val u = Users.USERS
                val e = ExtraPermissions.EXTRA_PERMISSIONS

                val optionalUser = select()
                    .from(u)
                    .where(u.EMAIL.eq(email))
                    .fetchOptionalInto(User::class.java)
                if (optionalUser.isEmpty)
                    null
                else {
                    val user = optionalUser.get()

                    val list = select()
                        .from(e)
                        .innerJoin(u).on(e.USER_ID.eq(u.ID))
                        .fetch { it.into(e).into(ExtraPermission::class.java) }

                    InboundUser(user, list)
                }
            }
    }
}