package ray.eldath.offgrid.util

import java.util.*

data class Either<out L, out R>(val left: L?, val right: R?) {

    val leftOrThrow: L
        get() = left ?: throw NullPointerException("left value of $this is unset")
    val rightOrThrow: R
        get() = right ?: throw NullPointerException("right value of $this is unset")

    val notHaveLeft: Boolean
        get() = left == null
    val notHaveRight: Boolean
        get() = right == null
    val haveLeft: Boolean
        get() = left != null
    val haveRight: Boolean
        get() = right != null

    fun <A, B> map(mapper: (L?, R?) -> Either<A?, B?>) = mapper(left, right)

    fun <T> mapLeft(mapper: (L?) -> T?) = mapper(left) or right
    fun <T> mapLeftOrThrow(mapper: (L) -> T) = mapper(leftOrThrow) or right

    fun <T> mapRight(mapper: (R?) -> T?) = left or mapper(right)
    fun <T> mapRightOrThrow(mapper: (R) -> T) = left or mapper(rightOrThrow)
}

fun <L> L.toLeft() = Either(this, null)
fun <R> R.toRight() = Either(null, this)
fun <L> Optional<L>.toLeft() = this.get().toLeft()
fun <R> Optional<R>.toRight() = this.get().toRight()
fun <L, R> Pair<L?, R?>.toEither() = Either(this.first, this.second)
infix fun <L, R> L?.or(right: R?) = Either(this, right)