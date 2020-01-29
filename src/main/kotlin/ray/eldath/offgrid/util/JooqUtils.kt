package ray.eldath.offgrid.util

import org.jooq.Converter

class PermissionConverter : Converter<String, Permission> {

    override fun from(databaseObject: String): Permission = Permission.fromId(databaseObject)
    override fun to(userObject: Permission): String = userObject.id

    override fun fromType(): Class<String> = String::class.java
    override fun toType(): Class<Permission> = Permission::class.java
}

class UserRoleConverter : Converter<Int, UserRole> {

    override fun from(databaseObject: Int): UserRole = UserRole.fromId(databaseObject)
    override fun to(userObject: UserRole): Int = userObject.id

    override fun fromType(): Class<Int> = Int::class.java
    override fun toType(): Class<UserRole> = UserRole::class.java
}