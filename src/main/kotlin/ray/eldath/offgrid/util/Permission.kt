package ray.eldath.offgrid.util

import ray.eldath.offgrid.util.Permission.Companion.expand

enum class Permission(
    val id: String,
    vararg val childrenPermissions: Permission? = arrayOf()
) {
    ListUser("U_L"),
    ModifyUser("U_M"),
    DeleteUser("U_D"),
    User("U", ListUser, ModifyUser, DeleteUser),

    ListUserApplication("UA_L"),
    ApproveUserApplication("UA_A", ListUserApplication),
    RejectUserApplication("UA_R", ListUserApplication),
    UserApplication("UA", ApproveUserApplication, RejectUserApplication),

    ListDataSource("DS_L"),
    CreateDataSource("DS_C"),
    ModifyDataSource("DS_M"),
    DeleteDataSource("DS_D"),
    DataSource(
        "DS",
        ListDataSource,
        CreateDataSource,
        ModifyDataSource,
        DeleteDataSource
    ),

    ListEndpoint("E_L"),
    CreateEndpoint("E_C"),
    ModifyEndpoint("E_M"),
    DeleteEndpoint("E_D"),
    Endpoint("E", ListEndpoint, CreateEndpoint, ModifyEndpoint, DeleteEndpoint),

    SelfComputationResult("CRs"),
    AllComputationResult("CRa", SelfComputationResult),
    ComputationResult("CR", AllComputationResult),

    Graph("G"), // implies forbidden model & provider, etc.

    InternalMetrics("M_I"),
    PanelMetrics("M_P"),
    Metrics("M", InternalMetrics, PanelMetrics),

    Root("ROOT", User, UserApplication, DataSource, Endpoint, ComputationResult, Graph, Metrics);

    val rootId = id.replaceAfter("_", "").replace("_", "")

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id }

        fun Permission.expand(): Sequence<Permission> =
            if (this.childrenPermissions.isEmpty())
                sequenceOf(this)
            else this.childrenPermissions.asSequence()
                .filterNotNull()
                .flatMap { it.expand() }
                .plus(this).distinct()

        fun Array<out Permission>.expand(): List<Permission> = flatMap { it.expand().toList() }

        fun Collection<Permission>.expand(): List<Permission> = flatMap { it.expand().toList() }
    }
}

enum class UserRole(val id: Int, vararg defaultPermissions: Permission) {
    PlatformAdmin(
        30,
        Permission.User,
        Permission.UserApplication,
        Permission.Graph,
        Permission.DataSource,
        Permission.Endpoint
    ), // UserAdmin + OperationAdmin
    UserAdmin(
        31,
        Permission.User,
        Permission.UserApplication
    ),
    OperationAdmin(
        32,
        Permission.Graph,
        Permission.DataSource,
        Permission.Endpoint
    ),

    MetricsAdmin(33, Permission.Metrics),
    SelfComputationAdmin(2, Permission.SelfComputationResult),
    Root(0, Permission.Root);

    val defaultPermissions = defaultPermissions.expand()

    companion object {
        fun fromId(id: Int) = values().first { it.id == id }

        fun fromPermission(permission: Permission) = values().filter { it.defaultPermissions.contains(permission) }
    }
}