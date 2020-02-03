package ray.eldath.offgrid.util

import ray.eldath.offgrid.util.Permission.Companion.expand

enum class Permission(
    val id: String,
    vararg val childrenPermissions: Permission? = arrayOf(),
    val rootId: String = id
) {
    ListUser("U_L", rootId = "U"),
    CreateUser("U_C", rootId = "U"),
    ModifyUserData("U_DM", rootId = "U"),
    ModifyUserPermission("U_PM", rootId = "U"),
    DeleteUser("U_D", rootId = "U"),
    User("U", ListUser, CreateUser, ModifyUserData, ModifyUserPermission, DeleteUser),

    ApproveUserApplication("UA_A", rootId = "UA"),
    RejectUserApplication("UA_R", rootId = "UA"),
    UserApplication("UA", ApproveUserApplication, RejectUserApplication),

    ListProviderRegistry("PR_L", rootId = "PR"),
    CreateProviderRegistry("PR_C", rootId = "PR"),
    UpdateProviderRegistry("PR_U", rootId = "PR"),
    DeleteProviderRegistry("PR_D", rootId = "PR"),
    ProviderRegistry(
        "PR",
        ListProviderRegistry,
        CreateProviderRegistry,
        UpdateProviderRegistry,
        DeleteProviderRegistry
    ),

    ListModelRegistry("MR_L", rootId = "MR"),
    CreateModelRegistry("MR_C", rootId = "MR"),
    UpdateModelRegistry("MR_U", rootId = "MR"),
    DeleteModelRegistry("MR_D", rootId = "MR"),
    ModelRegistry("MR", ListModelRegistry, CreateModelRegistry, UpdateModelRegistry, DeleteModelRegistry),

    SelfComputationResult("CRs"),
    AllComputationResult("CRa", SelfComputationResult, rootId = "CR"),
    ComputationResult("CR", AllComputationResult, rootId = "CR"),

    Graph("G"), // implies forbidden model & provider, etc.

    SelfProviderMetrics("M_Ps", rootId = "M"),
    AllProviderMetrics("M_Pa", SelfProviderMetrics, rootId = "M"),
    SelfModelMetrics("M_Ms", rootId = "M"),
    AllModelMetrics("M_Ma", SelfModelMetrics, rootId = "M"),
    SystemMetrics("M_S", rootId = "M"),
    Metrics("M", SystemMetrics, AllModelMetrics, AllProviderMetrics),

    Root("ROOT", User, UserApplication, ProviderRegistry, ModelRegistry, ComputationResult, Graph, Metrics);

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id }

        fun Permission.expand(): List<Permission> =
            if (this.childrenPermissions.isEmpty())
                listOf(this)
            else this.childrenPermissions
                .filterNotNull()
                .flatMap { it.expand() }
                .toMutableList().also { it.add(this) }

        fun Array<out Permission>.expand(): List<Permission> = flatMap { it.expand() }

        fun List<Permission>.expand(): List<Permission> = flatMap { it.expand() }
    }
}

enum class UserRole(val id: Int, vararg defaultPermissions: Permission) {
    PlatformAdmin(
        30,
        Permission.User,
        Permission.UserApplication,
        Permission.Graph,
        Permission.ProviderRegistry,
        Permission.ModelRegistry
    ), // UserAdmin + OperationAdmin
    UserAdmin(
        31,
        Permission.User,
        Permission.UserApplication
    ),
    OperationAdmin(
        32,
        Permission.Graph,
        Permission.ProviderRegistry,
        Permission.ModelRegistry
    ),

    MetricsAdmin(33, Permission.Metrics),
    SelfComputationAdmin(2, Permission.SelfComputationResult),
    SelfProviderAdmin(1, Permission.SelfProviderMetrics),
    Root(0, Permission.Root);

    val defaultPermissions = defaultPermissions.expand()

    companion object {
        fun fromId(id: Int) = values().first { it.id == id }

        fun fromPermission(permission: Permission) = values().filter { it.defaultPermissions.contains(permission) }
    }
}