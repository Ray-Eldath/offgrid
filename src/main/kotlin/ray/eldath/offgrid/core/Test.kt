package ray.eldath.offgrid.core

import org.slf4j.LoggerFactory

object TestLog {
    private val logger = LoggerFactory.getLogger(javaClass)

    @JvmStatic
    fun main(args: Array<String>) {
        logger.debug("debug log message")
        logger.info("info log message")
        logger.warn("warn log message")
        logger.error("error log message")
    }
}