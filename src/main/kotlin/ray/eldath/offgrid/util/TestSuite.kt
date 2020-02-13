package ray.eldath.offgrid.util

import org.slf4j.LoggerFactory

object TestSuite {
    private val logger = LoggerFactory.getLogger("test suite")

    fun debug(msg: String) = logger.debug(msg)
}