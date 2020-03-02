package ray.eldath.offgrid.util

enum class OAuthScope(vararg val permissions: Permission) {
    Profile, Grafana(Permission.PanelMetrics);

    val id: String = name.toLowerCase()

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id }
    }
}