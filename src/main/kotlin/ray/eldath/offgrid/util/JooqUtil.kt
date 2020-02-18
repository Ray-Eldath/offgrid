package ray.eldath.offgrid.util

import org.jooq.Converter
import org.jooq.DSLContext
import org.jooq.impl.DSL
import ray.eldath.offgrid.core.Core

fun <T> transaction(context: DSLContext = Core.jooqContext, block: DSLContext.() -> T): T {
    var a: T? = null
    context.transaction { cfg ->
        a = DSL.using(cfg).block()
    }
    return a!!
}

class PermissionConverter : Converter<String, Permission> {

    override fun from(databaseObject: String): Permission = Permission.fromId(databaseObject)!!
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

class UserStateConverter : Converter<Int, UserState> {

    override fun from(databaseObject: Int): UserState = UserState.fromId(databaseObject)
    override fun to(userObject: UserState): Int = userObject.id

    override fun fromType(): Class<Int> = Int::class.java
    override fun toType(): Class<UserState> = UserState::class.java
}