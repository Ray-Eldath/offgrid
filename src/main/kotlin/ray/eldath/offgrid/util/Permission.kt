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

    ListProviderRegistry("PR_L"),
    CreateProviderRegistry("PR_C"),
    UpdateProviderRegistry("PR_U"),
    DeleteProviderRegistry("PR_D"),
    ProviderRegistry(
        "PR",
        ListProviderRegistry,
        CreateProviderRegistry,
        UpdateProviderRegistry,
        DeleteProviderRegistry
    ),

    ListModelRegistry("MR_L"),
    CreateModelRegistry("MR_C"),
    UpdateModelRegistry("MR_U"),
    DeleteModelRegistry("MR_D"),
    ModelRegistry("MR", ListModelRegistry, CreateModelRegistry, UpdateModelRegistry, DeleteModelRegistry),

    SelfComputationResult("CRs"),
    AllComputationResult("CRa", SelfComputationResult),
    ComputationResult("CR", AllComputationResult),

    Graph("G"), // implies forbidden model & provider, etc.

    InternalMetrics("M_I"),
    PanelMetrics("M_P"),
    Metrics("M", InternalMetrics, PanelMetrics),

    Root("ROOT", User, UserApplication, ProviderRegistry, ModelRegistry, ComputationResult, Graph, Metrics);

    val rootId = id.replaceAfter("_", "").replace("_", "")

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id }

        fun Permission.expand(): List<Permission> =
            if (this.childrenPermissions.isEmpty())
                listOf(this)
            else this.childrenPermissions
                .filterNotNull()
                .flatMap { it.expand() }
                .toMutableList().also { it.add(this) }.distinct()

        fun Array<out Permission>.expand(): List<Permission> = flatMap { it.expand() }.distinct()

        fun Collection<Permission>.expand(): List<Permission> = flatMap { it.expand() }.distinct()
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
    Root(0, Permission.Root);

    val defaultPermissions = defaultPermissions.expand()

    companion object {
        fun fromId(id: Int) = values().first { it.id == id }

        fun fromPermission(permission: Permission) = values().filter { it.defaultPermissions.contains(permission) }
    }
}