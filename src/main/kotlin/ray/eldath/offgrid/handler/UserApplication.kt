package ray.eldath.offgrid.handler

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.security.Security
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.jooq.Condition
import ray.eldath.offgrid.generated.offgrid.tables.Authorizations
import ray.eldath.offgrid.generated.offgrid.tables.ExtraPermissions
import ray.eldath.offgrid.generated.offgrid.tables.UserApplications
import ray.eldath.offgrid.generated.offgrid.tables.Users
import ray.eldath.offgrid.generated.offgrid.tables.pojos.UserApplication
import ray.eldath.offgrid.model.InboundExtraPermission
import ray.eldath.offgrid.model.toExtraExchangeable
import ray.eldath.offgrid.util.*
import ray.eldath.offgrid.util.DirectEmailUtil.sendEmail
import ray.eldath.offgrid.util.ErrorCodes.commonBadRequest
import ray.eldath.offgrid.util.ErrorCodes.commonNotFound
import ray.eldath.offgrid.util.Permission.Companion.expand
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

class ListUserApplications(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    data class ListResponseEntry(val id: Int, val email: String, val username: String)
    data class ListResponse(val total: Int, val result: List<ListResponseEntry>)

    private val pageLens = Query.int().defaulted("page", 1, "the n-th page of result")
    private val pageSizeLens =
        Query.int().defaulted("pre_page", 20, "the size of elements that one page should contain.")

    private val emailLens = Query.optional("email", "fuzzily filter by email")
    private val usernameLens = Query.optional("username", "fuzzily filter by username")

    private val handler: HttpHandler = { req ->
        credentials(req).requirePermission(Permission.ListUserApplication)

        val page = pageLens(req)
        val pageSize = pageSizeLens(req)

        val email = emailLens(req)
        val username = usernameLens(req)

        transaction {
            val ua = UserApplications.USER_APPLICATIONS

            val conditions = arrayListOf<Condition>().also {
                if (email != null)
                    it += ua.EMAIL.likeIgnoreCase("%$email%")
                if (username != null)
                    it += ua.USERNAME.likeIgnoreCase("%$username%")
            }

            ListResponse(
                total = selectCount().from(ua).where(conditions).fetchOne(0, Int::class.java),
                result = selectDistinct(*ua.fields()).from(ua).where(conditions)
                    .orderBy(ua.ID).limit(pageSize).offset((page - 1) * pageSize)
                    .fetch { it.into(ua).into(UserApplication::class.java) }
                    .map { ListResponseEntry(it.id, it.email, it.username) }
            )
        }.let { Response(Status.OK).with(responseLens of it) }
    }

    override fun compile(): ContractRoute =
        "/applications" meta {
            summary = "Query register applications, ordered by id"
            description = "Filter register applications with given predicates, note that none of them is required."
            tags += RouteTag.UserApplication
            security = optionalSecurity

            queries += listOf(pageLens, pageSizeLens, emailLens, usernameLens)

            outJson()
            returning(
                Status.OK,
                responseLens to ListResponse(
                    1,
                    listOf(ListResponseEntry(1, "alpha@beta.omega", "False Ray Eldath"))
                )
            )
        } bindContract Method.GET to handler

    companion object {
        private val responseLens = Body.auto<ListResponse>().toLens()
    }
}

class ApproveUserApplication(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class ApproveRequest(val roleId: Int, val extraPermissions: List<InboundExtraPermission>? = null) {
        init {
            if (roleId !in UserRole.values().map { it.id })
                throw commonBadRequest("invalid roleId")()
        }
    }

    private fun handler(id: Int, useless: String): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.ApproveUserApplication)

        val json = requestLens(req)
        val role = UserRole.fromId(json.roleId)

        transaction {
            val ua = UserApplications.USER_APPLICATIONS

            val application = UserApplicationHandler.getByIdChecked(id)
            val user = newRecord(Users.USERS).apply {
                username = application.username
                email = application.email
            }
            user.insert()

            val authorization = newRecord(Authorizations.AUTHORIZATIONS).apply {
                userId = user.id
                hashedPassword = application.hashedPassword
                this.role = role
            }
            authorization.insert()

            json.extraPermissions.orEmpty().map {
                newRecord(ExtraPermissions.EXTRA_PERMISSIONS).apply {
                    authorizationId = authorization.userId
                    permissionId = Permission.fromId(it.id)
                    isShield = it.isShield
                }
            }.let { batchInsert(it) }

            deleteFrom(ua)
                .where(ua.EMAIL.eq(application.email)).execute()

            application
        }.let { application ->
            ctx.launch {
                sendApproveEmail(
                    application.email,
                    application.username,
                    LocalDateTime.now(),
                    role,
                    json.extraPermissions
                        .orEmpty()
                        .filterNot { it.isShield }
                        .mapNotNull { Permission.fromId(it.id) }
                )
            }
        }

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/application" / Path.int().of("id", "id of the application") / "approve" meta {
            summary = "Approve the registration application"
            description = "An email will be sent to notify the user."
            security = optionalSecurity
            tags += RouteTag.UserApplication

            allJson()
            receiving(
                requestLens to ApproveRequest(
                    UserRole.MetricsAdmin.id,
                    listOf(
                        Permission.User.toExtraExchangeable(false),
                        Permission.SystemMetrics.toExtraExchangeable(true)
                    )
                )
            )
            returning(Status.OK to "given application has been deleted, and the user properly is created")
        } bindContract Method.POST to ::handler

    companion object {
        private val ctx = CoroutineScope(Dispatchers.IO)
        private val requestLens = Body.auto<ApproveRequest>().toLens()

        suspend fun sendApproveEmail(
            email: String,
            username: String,
            time: LocalDateTime,
            role: UserRole,
            extraPermissions: List<Permission>
        ) {
            val displayPermissions =
                if (extraPermissions.isEmpty()) ""
                else " 和额外权限 " + extraPermissions.expand().joinToString(limit = 100)
            sendEmail("[Offgrid] 注册成功：您的申请已被批准", "applicationapproved", email) {
                """
                    尊敬的 $username：
                    
                    欢迎使用 Offgrid！您的注册申请已于 ${time.format(RFC_1123_DATE_TIME)} 被您组织的管理员正式批准。
                    该管理员为您分配了身份 ${role.name}$displayPermissions。关于这些权限的具体含义，请参阅用户手册，或咨询您组织的 Offgrid 管理员。
                    
                    您现在可以使用该邮箱和您预先设定的密码登录 Offgrid 了。祝使用愉快！
                    
                    Offgrid.
                    
                    
                    
                    
                    
                    您收到这封邮件是因为您此先在 Offgrid 上提交的注册申请现已被管理员正式批准。我们已经使用您提交的用户名、密码和管理员为您分配的身份以及额外权限（如果有）为您初始化了账户。若此注册与您无关，请忽略本邮件。
                    
                    请留意您组织的账户活跃要求：您的组织可能会删除注册后若干日内未登录，或太久没有登录的账户。
                    
                    
                    此邮件由 Offgrid 系统自动发出，请勿直接回复。若有更多问题请联系您组织的 Offgrid 管理员。
                """.trimIndent()
            }
        }
    }
}

class RejectUserApplication(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    private fun handler(id: Int, useless: String): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.RejectUserApplication)

        transaction {
            val ua = UserApplications.USER_APPLICATIONS

            UserApplicationHandler
                .getByIdChecked(id)
                .sidecar {
                    update(ua)
                        .set(ua.IS_APPLICATION_PENDING, false)
                        .where(ua.ID.eq(it.id)).execute()
                }
        }.let { ctx.launch { sendRejectEmail(it.email, it.username, LocalDateTime.now()) } }

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/application" / Path.int().of("id", "id of the application") / "reject" meta {
            summary = "Reject the registration application"
            description =
                "Given application as well as any further applications linked with this email will all be rejected, " +
                        "an email will be sent to notify the user as well."
            security = optionalSecurity
            tags += RouteTag.UserApplication

            returning(Status.OK to "given application has been marked as rejected")
        } bindContract Method.GET to ::handler

    companion object {
        private val ctx = CoroutineScope(Dispatchers.IO)

        suspend fun sendRejectEmail(email: String, username: String, time: LocalDateTime) {
            sendEmail("[Offgrid] 注册失败：您的申请已被拒绝", "applicationrejected", email) {
                """
                    尊敬的 $username：
                    
                    我们遗憾地通知您，您向您的组织提交的注册申请已于 ${time.format(RFC_1123_DATE_TIME)} 被您组织的管理员拒绝。
                    
                    由于安全策略，任何使用本邮箱的后续注册请求都将被直接拒绝。若您仍需要注册账户，请联系您组织的 Offgrid 管理员以重置您的账户状态。
                    
                    Offgrid.
                    
                    
                    
                    
                    
                    您收到这封邮件是因为您此先在 Offgrid 上提交的注册申请现已被您管理员拒绝。
                    
                    
                    此邮件由 Offgrid 系统自动发出，请勿直接回复。若有更多问题请联系您组织的 Offgrid 管理员。
                """.trimIndent()
            }
        }
    }
}

class ResetUserApplication(credentials: Credentials, optionalSecurity: Security) :
    ContractHandler(credentials, optionalSecurity) {

    private fun handler(id: Int, useless: String): HttpHandler = { req ->
        credentials(req).requirePermission(Permission.RejectUserApplication)

        transaction {
            val ua = UserApplications.USER_APPLICATIONS

            UserApplicationHandler.getByIdChecked(id).let {
                deleteFrom(ua)
                    .where(ua.ID.eq(it.id)).execute()
            }
        }

        Response(Status.OK)
    }

    override fun compile(): ContractRoute =
        "/application" / Path.int().of("id", "id of the application") / "reset" meta {
            summary = "Reset the registration application"
            description =
                "Clear related entry in the database. After this, banned applicant could submit another application again."
            security = optionalSecurity
            tags += RouteTag.UserApplication

            returning(Status.OK to "given application entry has been deleted")
        } bindContract Method.GET to ::handler
}

object UserApplicationHandler {

    fun getByIdChecked(id: Int) =
        transaction {
            val ua = UserApplications.USER_APPLICATIONS

            select()
                .from(ua)
                .where(ua.ID.eq(id))
                .fetchOptional { it.into(ua).into(UserApplication::class.java) }
                .run {
                    if (isEmpty)
                        throw commonNotFound()()
                    else get()
                }
        }
}