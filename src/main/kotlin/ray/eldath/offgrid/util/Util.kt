package ray.eldath.offgrid.util

fun <T> T.sidecar(aside: (T) -> Unit): T {
    val r = this
    aside(r)
    return r
}