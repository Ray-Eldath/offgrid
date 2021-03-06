package ray.eldath.offgrid.component

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.graphite.GraphiteConfig
import io.micrometer.graphite.GraphiteMeterRegistry
import ray.eldath.offgrid.core.Core

object Metrics {
    val registry by lazy {
        if (Core.debug)
            SimpleMeterRegistry()
        else
            object : GraphiteConfig {
                override fun host(): String = Core.getEnvSafe("OFFGRID_GRAPHITE_HOST")

                override fun get(key: String): String? = null
            }.let { GraphiteMeterRegistry(it, Clock.SYSTEM) }
    }
}