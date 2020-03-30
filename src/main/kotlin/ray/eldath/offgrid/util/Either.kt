package ray.eldath.offgrid.util

import java.lang.Exception
import java.util.*

sealed class Either<out A, out B> {
    class Left<A>(val value: A) : Either<A, Nothing>()
    class Right<B>(val value: B) : Either<Nothing, B>()

    inline fun <C> map(f: (B) -> C): Either<A, C> = flatMap { Right(f(it)) }

    inline fun rightOrThrow(exception: () -> Exception) =
        when (this) {
        }
}

inline fun <A, B, C> Either<A, B>.flatMap(f: (B) -> Either<A, C>): Either<A, C> =
    when (this) {
        is Either.Left -> this
        is Either.Right -> f(this.value)
    }

fun <L> L.asLeft() = Either.Left(this)
fun <R> R.asRight() = Either.Right(this)
fun <L> Optional<L>.asLeft() = this.get().asLeft()
fun <R> Optional<R>.asRight() = this.get().asRight()