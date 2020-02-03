import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ray.eldath.offgrid.util.HighLightConverter
import ray.eldath.offgrid.util.LoggerHighLightConverter

conversionRule("highlight", HighLightConverter)
conversionRule("loggerHighlight", LoggerHighLightConverter)

def FILE_PATTERN = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

appender("Console", ConsoleAppender) {
    withJansi = false
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %loggerHighlight(%logger{36}) - %highlight(%msg) %n"
    }

    filter(ThresholdFilter) { level = DEBUG }
}

appender("File_WARN", RollingFileAppender) {
    rollingPolicy(TimeBasedRollingPolicy) {
        fileNamePattern = "log/warn.%d{yyyy-MM-dd}.log"
        maxHistory = 30
        totalSizeCap = "1GB"
    }

    filter(ThresholdFilter) { level = WARN }
    encoder(PatternLayoutEncoder) { pattern = FILE_PATTERN }
}

appender("File_ERROR", RollingFileAppender) {
    rollingPolicy(SizeAndTimeBasedRollingPolicy) {
        fileNamePattern = "log/error.%d{yyyy-MM-dd}.%i.log"
        maxFileSize = "5MB"
        totalSizeCap = "2GB"
    }

    filter(ThresholdFilter) { level = ERROR }
    encoder(PatternLayoutEncoder) { pattern = FILE_PATTERN }
}

statusListener(NopStatusListener)
// logger("java.sql.Connection", DEBUG)
// logger("java.sql.Statement", DEBUG)

root(INFO, ["Console", "File_WARN", "File_ERROR"])