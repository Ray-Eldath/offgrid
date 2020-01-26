package ray.eldath.offgrid.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.UserRole

object Users : IntIdTable() {
    val username = varchar("username", 20)
    val email = text("email").uniqueIndex() // avatar use Gravatar.
    val emailConfirmed = bool("is_email_confirmed").default(false)
}

object Authorizations : IntIdTable() {
    override val id: Column<EntityID<Int>> = reference("user_id", Users.id)

    val hashedPassword = blob("password_hashed")
    val role = integer("role")
}

object ExtraPermissions : IntIdTable() {
    override val id = reference("authorization_id", Authorizations.id)

    val permission = varchar("permission_id", 5)
    val shield = bool("is_shield").default(true)
}

class ExtraPermission(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ExtraPermission>(ExtraPermissions)

    val permission: Permission by ExtraPermissions.permission.transform({ it.id }, { Permission.fromId(it) })
    val isShield by ExtraPermissions.shield
}

class Authorization(id: EntityID<Int>, val extraPermissions: Set<ExtraPermission>) : IntEntity(id) {
    companion object : IntEntityClass<Authorization>(Authorizations)

    val userId by Authorizations.id
    var hashedPassword by Authorizations.hashedPassword
    var role: UserRole by Authorizations.role.transform({ it.id }, { UserRole.fromId(it) })

    fun requirePermission(permission: Permission) {
        role.defaultPermissions
    }
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var userId by Users.id
    var username by Users.username
    var email by Users.username
    var authorization by Authorization referencedOn Users.id
}