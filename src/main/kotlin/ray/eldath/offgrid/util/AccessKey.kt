package ray.eldath.offgrid.util

import java.util.*

data class AccessKey(val id: String, val secret: String) {

    companion object {
        fun generate() =
            Array(3) { UUID.randomUUID().toString() }
                .let { AccessKey(it[0], (it[1] + it[2]).base64().asIterable().shuffled().joinToString("")) }
    }

    fun toPair(): Pair<String, String> = id to secret
}