package ray.eldath.offgrid.util

enum class UserState(val id: Int) {
    Normal(0),
    Banned(1);

    companion object {
        fun fromId(id: Int) = values().first { it.id == id }
    }
}