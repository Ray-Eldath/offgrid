package ray.eldath.offgrid.util

import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest
import com.aliyuncs.exceptions.ClientException
import com.aliyuncs.exceptions.ServerException
import com.aliyuncs.http.MethodType
import com.aliyuncs.profile.DefaultProfile
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import ray.eldath.offgrid.core.Core

object DirectEmailUtil {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val REGION = Core.getEnv("OFFGRID_ALIYUN_DIRECTMAIL_REGION") ?: "cn-hangzhou"

    private val aliyunClient =
        DefaultProfile.getProfile(
            REGION,
            Core.getEnvSafe("OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_ID"),
            Core.getEnvSafe("OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_SECRET") // require permission: AliyunDirectMailFullAccess...... :-(
        ).also {
            if (REGION != "cn-hangzhou")
                DefaultProfile.addEndpoint(REGION, "Dm", "dm.$REGION.aliyuncs.com")
        }.let { DefaultAcsClient(it) }

    suspend fun sendEmail(
        subject: String,
        aliyunTag: String,
        toAddress: String,
        fromAlias: String = "Offgrid System",
        textBody: () -> String
    ) = coroutineScope {
        if (Core.debug) {
            TestSuite.debug("email tagged $aliyunTag with subject $subject attempt send to $toAddress:\n${textBody()}")
            return@coroutineScope
        }

        fun error(e: ClientException, type: String = "ClientException"): Unit =
            ("AliyunDirectMail: $type(errCode: ${e.errCode}) thrown when sendEmail to email address $toAddress " +
                    "with subject $subject and tag $aliyunTag").let {
                logger.error(it, e)

                throw (ErrorCodes.sendEmailFailed(toAddress, it + "\n $type: ${e.asJsonString()}"))()
            }

        try {
            val resp = SingleSendMailRequest().apply {
                accountName = "no-reply@qvq.ink"
                addressType = 1
                tagName = aliyunTag
                replyToAddress = true
                sysMethod = MethodType.POST
                clickTrace = "0"
                this.fromAlias = fromAlias
                this.toAddress = toAddress
                this.subject = subject
                this.textBody = textBody()
            }.let { aliyunClient.doAction(it) }
            if (!resp.isSuccess) {
                val info = "$toAddress with email subject $subject tagged $aliyunTag"
                val log =
                    "AliyunDirectMail: unsuccessful send attempt to $info with response: \n" +
                            "(${resp.status}) \n ${resp.httpContentString}"
                logger.error(log)
                throw (ErrorCodes.sendEmailFailed(toAddress, log))()
            }
        } catch (e: ServerException) {
            error(e, "ServerException")
        } catch (e: ClientException) {
            error(e)
        }
    }
}