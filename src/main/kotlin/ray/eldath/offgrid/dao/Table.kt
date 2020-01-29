package ray.eldath.offgrid.dao

import org.jetbrains.exposed.sql.Table

@Deprecated("use jOOQ now.")
object OffgridTables {
    val tables: Array<Table> = arrayOf(Users, Authorizations, ExtraPermissions)
}