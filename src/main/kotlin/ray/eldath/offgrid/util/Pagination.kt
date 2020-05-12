package ray.eldath.offgrid.util

object Pagination {
    fun offset(page: Int, pageSize: Int) = (page - 1) * pageSize
}