package ray.eldath.offgrid.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.*
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.MetricFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.routing.bind
import org.http4k.server.ApacheServer
import org.http4k.server.asServer
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import ray.eldath.offgrid.component.ApiException
import ray.eldath.offgrid.component.ApiExceptionHandler
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.component.Metrics
import ray.eldath.offgrid.handler.*
import ray.eldath.offgrid.util.RouteTag
import java.io.File


object Core {
    var debug = false
        private set
    val enableDebug: Boolean
        get() {
            debug = true
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.DEBUG
            System.err.println("debug mode is enabled.")
            return true
        }

    val security = BearerSecurity
    val credentials = BearerSecurity.credentials

    private val logger = LoggerFactory.getLogger(javaClass)
    private val allRoutes = arrayListOf<ContractHandler>()

    init {
        allRoutes += Login()
        allRoutes += Logout(security)
        allRoutes += Register()
        allRoutes += ConfirmEmail.ValidateUrlToken()
        allRoutes += ConfirmEmail.SubmitUserApplication()
        allRoutes += ResetPassword.Invoke()
        allRoutes += ResetPassword.Verify()
        allRoutes += ResetPassword.Submit()

        allRoutes += ListUserApplications(credentials, security)
        allRoutes += ApproveUserApplication(credentials, security)
        allRoutes += RejectUserApplication(credentials, security)
        allRoutes += ResetUserApplication(credentials, security)

        allRoutes += ListUsers(credentials, security)
        allRoutes += ModifyUser(credentials, security)
        allRoutes += BanUser(credentials, security)
        allRoutes += UnbanUser(credentials, security)
        allRoutes += DeleteUser(credentials, security)

        allRoutes += CreateDataSource(credentials, security)
        allRoutes += ModifyDataSource(credentials, security)

        allRoutes += Self(credentials, security)
        allRoutes += DeleteSelf(credentials, security)

        allRoutes += MetaUserModel(security)

        allRoutes += Echo()
        allRoutes += Require()

        allRoutes += Hydra.HydraCheck()
        allRoutes += Hydra.HydraLogin()
        allRoutes += Hydra.HydraConsent()
    }

    val jooqContext: DSLContext by lazy {
        if (!debug)
            HikariConfig().apply {
                poolName = "offgrid"
                jdbcUrl = getEnvSafe("OFFGRID_BACKEND_JDBC_URL")
                username = "offgrid"
                password = getEnvSafe("OFFGRID_DATABASE_PASSWORD")
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                metricRegistry = Metrics.registry
            }.let { DSL.using(HikariDataSource(it), SQLDialect.MYSQL) }
        else DSL.using("jdbc:mysql://localhost:3306/offgrid", "offgrid", "1234")
    }

    private const val ROOT = ""
    private val filterChain by lazy {
        (if (debug)
            DebuggingFilters.PrintRequest(debugStream = true)
                .also { System.err.println("filter PrintRequest is installed.") }
        else Filter.NoOp)
            .then(Filter { next -> { if (it.method == Method.OPTIONS) Response(Status.OK) else next(it) } })
            .then(ApiExceptionHandler.filter)
            .then(ServerFilters.CatchLensFailure)
            .then(Filter { next ->
                {
                    try {
                        next(it)
                    } catch (e: Exception) {
                        if (e !is ApiException)
                            logger.error("non-ApiException thrown when handling request: $it", e)
                        throw e
                    }
                }
            })
            .then(MetricFilters.Server.RequestCounter(Metrics.registry))
            .then(MetricFilters.Server.RequestTimer(Metrics.registry))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting Offgrid...")
        if (args.size == 1 && args[0] == "debug")
            enableDebug
        loadEnv()
        jooqContext // init after env are loaded
        val globalRenderer = OpenApi3(ApiInfo("Offgrid", "v1.0", "Backend API for Offgrid."), Jackson)
        val descPath = "/swagger.json"

        val http: HttpHandler =
            ROOT bind contract {
                renderer = globalRenderer
                descriptionPath = descPath
                routes += allRoutes
                    .map { it.compile() }
                    .let { r -> if (debug) r else r.filterNot { it.tags.contains(RouteTag.Debug) } }
                preSecurityFilter = filterChain
            }

        http.asServer(ApacheServer(8080)).start()
        println("Offgrid now ready for requests.")
    }

    private const val ENV_PREFIX = "offgrid.env"
    fun loadEnv() {
        val file = File(System.getProperty("user.dir") + "/.env.app")
        if (!file.exists())
            return

        file.bufferedReader().useLines { seq ->
            seq.map { it.split("=") }
                .filter { it.size >= 2 }
                .forEach {
                    System.setProperty("$ENV_PREFIX.${it[0]}", it.drop(1).joinToString(""))
                }
        }

        System.setProperty("$ENV_PREFIX.loaded", "true")
    }

    fun getEnv(key: String): String? {
        if (System.getProperty("$ENV_PREFIX.loaded") == null)
            loadEnv()
        return (System.getenv(key) ?: System.getProperty("offgrid.env.$key")).ifBlank { null }
    }

    fun getEnvSafe(key: String): String =
        getEnv(key) ?: throw NullPointerException("environment variables $key is not set")
}