@file:Suppress("TestFunctionName")

package ray.eldath.offgrid.test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.BiDiBodyLens
import ray.eldath.offgrid.util.ErrorCodes
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.Permission.Companion.expand
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isEqualTo

fun String.GET() = org.http4k.core.Request(Method.GET, this)
fun String.POST() = org.http4k.core.Request(Method.POST, this)
fun String.PUT() = org.http4k.core.Request(Method.PUT, this)
fun String.PATCH() = org.http4k.core.Request(Method.PATCH, this)
fun String.DELETE() = org.http4k.core.Request(Method.DELETE, this)

fun <T> Response.parse(lens: BiDiBodyLens<T>) = lens(this).also { println(it) }
fun Response.json() = jacksonObjectMapper().readTree(body.stream.bufferedReader().readText())

fun Response.expectOk() = also { expectThat(status).isEqualTo(Status.OK) }
fun Assertion.Builder<Response>.expectOk() = also { status().isEqualTo(Status.OK) }
fun Assertion.Builder<Response>.status(): Assertion.Builder<Status> = get { status }

fun <T> Assertion.Builder<T>.println(): Assertion.Builder<T> = get {
    println(this)
    this
}

fun permissionDeniedException(vararg require: Permission) =
    (ErrorCodes.permissionDenied(require.expand().toList()))()