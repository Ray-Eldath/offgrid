package ray.eldath.offgrid.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.UserRole

@Deprecated("use jOOQ now.")
object Users : IntIdTable() {
    val username = varchar("username", 20)
    val email = varchar("email", 50).uniqueIndex() // avatar use Gravatar.
    val emailConfirmed = bool("is_email_confirmed").default(false)
}

@Deprecated("use jOOQ now.")
object Authorizations : IdTable<Int>() {
    override val id = reference("user_id", Users.id)

    val hashedPassword = blob("password_hashed")
    val role = integer("role")
}

@Deprecated("use jOOQ now.")
object ExtraPermissions : IdTable<Int>() {
    override val id = reference("authorization_id", Authorizations.id)

    val permission = varchar("permission_id", 5)
    val shield = bool("is_shield").default(true)
}

@Deprecated("use jOOQ now.")
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var username by Users.username
    var email by Users.email
    var emailConfirmed by Users.emailConfirmed
    val authorization by Authorization referrersOn Authorizations.id
}

@Deprecated("use jOOQ now.")
class Authorization(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Authorization>(Authorizations)

    var hashedPassword by Authorizations.hashedPassword
    var role: UserRole by Authorizations.role.transform({ it.id }, { UserRole.fromId(it) })
    var user by User referencedOn Authorizations.id
}

@Deprecated("use jOOQ now.")
class ExtraPermission(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ExtraPermission>(ExtraPermissions)

    val permission: Permission by ExtraPermissions.permission.transform({ it.id }, { Permission.fromId(it) })
    val isShield by ExtraPermissions.shield
}