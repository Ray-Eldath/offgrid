package ray.eldath.offgrid.util

import java.util.Base64

object Base64 {
    val encoder = Base64.getEncoder()
    val decoder = Base64.getDecoder()
    val urlSafeEncoder = Base64.getUrlEncoder()
    val urlSafeDecoder = Base64.getUrlDecoder()
}

fun Base64.Encoder.encode(content: String) = encodeToString(content.toByteArray())

fun String.base64() = ray.eldath.offgrid.util.Base64.encoder.encode(this)
fun String.base64Url() = ray.eldath.offgrid.util.Base64.urlSafeEncoder.encode(this)

fun String.decodeBase64() = String(ray.eldath.offgrid.util.Base64.urlSafeDecoder.decode(this))
fun String.decodeBase64Url() = String(ray.eldath.offgrid.util.Base64.urlSafeDecoder.decode(this))