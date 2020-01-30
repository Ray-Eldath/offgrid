@file:Suppress("TestFunctionName")

package ray.eldath.offgrid.test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import strikt.api.Assertion

fun String.GET() = org.http4k.core.Request(Method.GET, this)

fun String.POST() = org.http4k.core.Request(Method.POST, this)

fun Response.json() = jacksonObjectMapper().readTree(body.stream.bufferedReader().readText())

fun Assertion.Builder<Response>.status(): Assertion.Builder<Status> = get { status }

fun <T> Assertion.Builder<T>.println(): Assertion.Builder<T> = get {
    println(this)
    this
}