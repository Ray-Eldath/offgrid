package ray.eldath.offgrid.util

enum class Permission(val id: String, vararg val childrenPermissions: Permission? = arrayOf()) {
    ListUser("U_L"),
    CreateUser("U_C"),
    ModifyUserData("U_DM"),
    ModifyUserPermission("U_PM"),
    DeleteUser("U_D"),
    User("U", ListUser, CreateUser, ModifyUserData, ModifyUserPermission, DeleteUser),

    ApproveUserApplication("UA_A"),
    RejectUserApplication("UA_R"),
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

    ListSelfComputationResult("CR_Ls"),
    ListAllComputationResult("CR_La", ListSelfComputationResult),
    TagSelfComputationResult("CR_Ta"),
    TagAllComputationResult("CR_Ta", TagSelfComputationResult),
    SelfComputationResult("CRs", ListSelfComputationResult, TagSelfComputationResult),
    ComputationResult("CR", ListAllComputationResult, TagAllComputationResult),

    Graph("G"), // implies forbidden model & provider, etc.

    SelfProviderMetrics("M_Ps"),
    AllProviderMetrics("M_Pa", SelfProviderMetrics),
    SelfModelMetrics("M_Ms"),
    AllModelMetrics("M_Ma", SelfModelMetrics),
    SystemMetrics("M_S"),
    Metrics("M", SystemMetrics, AllModelMetrics, AllProviderMetrics),

    Root("ROOT", User, UserApplication, ProviderRegistry, ModelRegistry, ComputationResult, Graph, Metrics);

    companion object {
        fun fromId(id: String) = values().first { it.id == id }

        fun expand(permission: Permission): List<Permission> {
            if (permission.childrenPermissions.isEmpty())
                return listOf(permission)
            val r = arrayListOf<Permission>()
            for (childPermission in permission.childrenPermissions)
                r.addAll(expand(permission))
            return r
        }

        fun expand(permissions: Array<out Permission>): List<Permission> =
            permissions.flatMap { expand(it) }
    }
}

enum class UserRole(val id: Int, val displayName: String, vararg defaultPermissions: Permission) {
    PlatformAdmin(
        30,
        "PlatformAdmin",
        Permission.User,
        Permission.UserApplication,
        Permission.Graph,
        Permission.ProviderRegistry,
        Permission.ModelRegistry
    ), // UserAdmin + OperationAdmin
    UserAdmin(
        31, "UserAdmin",
        Permission.User,
        Permission.UserApplication
    ),
    OperationAdmin(
        32, "OperationAdmin",
        Permission.Graph,
        Permission.ProviderRegistry,
        Permission.ModelRegistry
    ),

    MetricsAdmin(33, "MetricsAdmin", Permission.Metrics),
    SelfComputationAdmin(
        2, "SelfComputationAdmin",
        Permission.SelfComputationResult
    ),
    SelfProviderAdmin(1, "SelfProviderAdmin", Permission.SelfProviderMetrics),
    Root(0, "Root", Permission.Root);

    val defaultPermissions = Permission.expand(defaultPermissions)

    companion object {
        fun fromId(id: Int) = values().first { it.id == id }
    }
}