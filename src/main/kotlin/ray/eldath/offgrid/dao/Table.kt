package ray.eldath.offgrid.dao

import org.jetbrains.exposed.sql.Table

object OffgridTables {
    val tables: Array<Table> = arrayOf(Users, Authorizations, ExtraPermissions)
}